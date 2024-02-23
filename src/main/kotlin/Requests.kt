import kotlinx.coroutines.*
import okhttp3.*
import java.io.File
import java.time.Duration
import java.util.concurrent.Semaphore

// Using java semaphores and with context because kotlin semaphores don't have variable
//  amounts of permits

data class Query(val code: String, val value: String) {
    override fun toString(): String {
        return "$code=$value"
    }
}

class TermedClient(val term: String?) {
    private val client: OkHttpClient

    @Volatile
    private var inited = term == null
    private val initLock = Semaphore(1)

    init {
        val cookieJar = object : CookieJar {
            val cookies = mutableListOf<Cookie>()

            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                this.cookies.addAll(cookies)
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                return cookies
            }
        }

        client = OkHttpClient.Builder().cookieJar(cookieJar).build()
    }

    private fun tryInitTerm() {
        if (inited) {
            return
        }

        if (initLock.tryAcquire()) {
            // This will "make the cookies work"
            client.newCall(Request.Builder()
                .url("https://prodapps.isadm.oregonstate.edu/StudentRegistrationSsb/ssb/term/search?mode=search")
                .post(
                    FormBody.Builder()
                        .add("term", term!!)
                        .add("studyPath", "")
                        .add("studyPathText", "")
                        .add("startDatepicker", "")
                        .build()
                ).build()).execute().close()

            inited = true
        } else {
            // Waits until the other thread inits it
            initLock.acquire()
        }

        initLock.release()
    }

    fun call(request: Request): Response {
        tryInitTerm()
        return client.newCall(request).execute()
    }
}

private val termClients = mutableMapOf<String?, TermedClient>()
private val clientsSemaphore = Semaphore(1)

private val cache = RequestResponseCache(
        File("./cache.json.gz"),
        Duration.ofDays(30),
        1024L * 1024L * 1024L)

val requestSemaphore = Semaphore(20)

suspend fun cachedRequest(url: String, term: String? = null, withCleanClient: Boolean = false): String = withContext(Dispatchers.IO) {
    // Hope this doesn't cause any collisions
    val cacheKey = (term ?: "") + url

    val cacheVal1 = cache.getOrNull(cacheKey)
    if (cacheVal1 != null) {
        return@withContext cacheVal1
    }

    val request = Request.Builder().url(url).get().build()

    var client = if (withCleanClient) {
        TermedClient(term)
    } else {
        termClients[term]
    }

    if (client == null) {
        clientsSemaphore.acquire()

        client = termClients[term]
        if (client == null) {
            client = TermedClient(term)
            termClients[term] = client
        }

        clientsSemaphore.release()
    }

    var waited = false
    if (!requestSemaphore.tryAcquire()) {
        waited = true
        requestSemaphore.acquire()
    }

    if (waited) {
        val cacheVal2 = cache.getOrNull(cacheKey)

        if (cacheVal2 != null) {
            requestSemaphore.release()
            return@withContext cacheVal2
        }
    }

    val response = client.call(request).body!!.string()
    requestSemaphore.release()

    // Could it check if it is making the same request as a different cacheRequest as well
    //  (that shouldn't happen though)
    cache.set(cacheKey, response)

    response
}

suspend fun getTerms(): List<OptionResponse> {
    val response = cachedRequest(
        "https://prodapps.isadm.oregonstate.edu/StudentRegistrationSsb/ssb/classSearch/" +
                "getTerms?&offset=1&max=2147483647"
    )

    return json.decodeFromString<List<OptionResponse>>(response)
}

suspend fun getOptions(type: String, term: String): List<OptionResponse> {
    val response = cachedRequest(
        "https://prodapps.isadm.oregonstate.edu/StudentRegistrationSsb/ssb/classSearch/" +
                "get_$type?term=$term&offset=1&max=2147483647"
    )

    return json.decodeFromString(response)
}

suspend fun getSearch(search: Search): List<ClassDataResponse> {
    var offset = 0
    val classResponses = mutableListOf<ClassDataResponse>()
    while (true) {
        val response = cachedRequest(
            "https://prodapps.isadm.oregonstate.edu/StudentRegistrationSsb/ssb/searchResults/" +
                    "searchResults?$search&pageOffset=$offset&pageMaxSize=500",
            search.term,
            // Somehow the request is malformed in a way that once
            //  it gets one it returns the same things over and over
            true
        )

        val decodedResponse = json.decodeFromString<SearchResponse>(response)
        classResponses.addAll(decodedResponse.data)

        if (classResponses.size < decodedResponse.totalCount) {
            offset += 500
        } else {
            break
        }
    }

    return classResponses
}

suspend fun getLinks(crn: String, term: String): LinkedSearchResponse {
    val response = cachedRequest(
        "https://prodapps.isadm.oregonstate.edu/StudentRegistrationSsb/ssb/searchResults/" +
                "fetchLinkedSections?term=$term&courseReferenceNumber=$crn"
    )

    return json.decodeFromString(response)
}
