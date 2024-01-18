import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import okhttp3.*
import java.io.File
import java.time.Duration


private val cookieJar = object : CookieJar {
    val cookies = mutableListOf<Cookie>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        this.cookies.addAll(cookies)
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return cookies
    }
}
private val client = OkHttpClient.Builder().cookieJar(cookieJar).build()

private var currentTerm = ""

private val cache = RequestResponseCache(File("./cache.json.gz"), Duration.ofDays(30), 1000L * 1000L * 1000L)
private val cacheLock = Semaphore(1)

private val requestSemaphore = Semaphore(1)

data class Query(val code: String, val value: String) {
    override fun toString(): String {
        return "$code=$value"
    }
}

suspend fun tryCache(key: Pair<String, String>): String? {
    cacheLock.acquire()
    val cached = cache.getOrNull(key)
    cacheLock.release()

    return cached
}

fun setTerm(term: String) {
    // This will "make the cookies work"
    client.newCall(Request.Builder()
        .url("https://prodapps.isadm.oregonstate.edu/StudentRegistrationSsb/ssb/term/search?mode=search")
        .post(
            FormBody.Builder()
                .add("term", term)
                .add("studyPath", "")
                .add("studyPathText", "")
                .add("startDatepicker", "")
                .build()
        ).build()).execute()

    currentTerm = term
}

// TODO: Probably better to use requestSemaphore.with
suspend fun cachedRequest(url: String, term: String? = null): String {
    val cacheKey = Pair(url, term ?: "")
    val cacheVal1 = tryCache(cacheKey)
    if (cacheVal1 != null) {
        return cacheVal1
    }

    val request = Request.Builder().url(url).get().build()

    if (!requestSemaphore.tryAcquire()) {
        requestSemaphore.acquire()

        val cacheVal2 = tryCache(cacheKey)
        if (cacheVal2 != null) {
            requestSemaphore.release()
            return cacheVal2
        }
    }

    if (term != null && currentTerm != term) {
        setTerm(term)
    }

    val response = client.newCall(request).execute().body!!.string()
    requestSemaphore.release()

    cacheLock.acquire()
    cache.set(cacheKey, response)
    cacheLock.release()

    return response
}

fun getTerms() = coroutineScope.async {
    val response = cachedRequest(
        "https://prodapps.isadm.oregonstate.edu/StudentRegistrationSsb/ssb/classSearch/" +
                "getTerms?&offset=1&max=2147483647"
    )

    json.decodeFromString<List<Option>>(response)
}

fun getOptions(type: String, term: String) = coroutineScope.async {
    val response = cachedRequest(
        "https://prodapps.isadm.oregonstate.edu/StudentRegistrationSsb/ssb/classSearch/" +
                "get_$type?term=$term&offset=1&max=2147483647"
    )

    json.decodeFromString<List<Option>>(response)
}

fun getSearch(search: Search) = coroutineScope.async {
    var offset = 0
    val classResponses = mutableListOf<ClassData>()
    while (true) {
        val response = cachedRequest(
            "https://prodapps.isadm.oregonstate.edu/StudentRegistrationSsb/ssb/searchResults/" +
                    "searchResults?$search&pageOffset=$offset&pageMaxSize=500",
            search.term
        )

        val decodedResponse = json.decodeFromString<SearchResponse>(response)
        classResponses.addAll(decodedResponse.data)

        if (classResponses.size < decodedResponse.totalCount) {
            offset += 500
        } else {
            break
        }
    }

    classResponses
}
