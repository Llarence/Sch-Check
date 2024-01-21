import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
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

@Volatile
private var currentTerm = ""
// Term lock has each thread remove one permit if it is using term
//  then if a thread needs to change term it acquires all the permits
//  A thread can only acquire all the permits if it has writeTermLock
//  The threads that want to change the term wait on termChange until
//  the term changes where they try to acquire writeTermLock.
//  There is probably better way
private val termLock = Semaphore(Int.MAX_VALUE)
private val writeTermLock = Semaphore(1)
private val termChange = Object()

private val cache = RequestResponseCache(
        File("./cache.json.gz"),
        Duration.ofDays(30),
        1024L * 1024L * 1024L)

private val requestSemaphore = Semaphore(5)

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
        ).build()).execute().close()

    currentTerm = term
}

// TODO: Check what synchronized does (and if it is deprecated)
fun setTermWhenPossible(term: String) {
    while (true) {
        if (writeTermLock.tryAcquire()) {
            if (term != currentTerm) {
                termLock.acquire(Int.MAX_VALUE - 1)
                setTerm(term)
                termLock.release(Int.MAX_VALUE - 1)
            }

            writeTermLock.release()
            synchronized(termChange) { termChange.notifyAll() }

            break
        } else {
            synchronized(termChange) { termChange.wait() }
        }
    }
}

suspend fun cachedRequest(url: String, term: String? = null): String = withContext(Dispatchers.IO) {
    // Hope this doesn't cause any collisions
    val cacheKey = (term ?: "") + url

    val cacheVal1 = cache.getOrNull(cacheKey)
    if (cacheVal1 != null) {
        return@withContext cacheVal1
    }

    val request = Request.Builder().url(url).get().build()

    var waited = false
    if (term != null) {
        termLock.acquire()

        if (term != currentTerm) {
            waited = true
            setTermWhenPossible(term)
        }
    }

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

    println("in ${requestSemaphore.availablePermits()}")
    val response = client.newCall(request).execute().body!!.string()
    println("out")
    requestSemaphore.release()
    if (term != null) {
        termLock.release()
    }

    cache.set(cacheKey, response)

    response
}

fun getTerms() = coroutineScope.async {
    val response = cachedRequest(
        "https://prodapps.isadm.oregonstate.edu/StudentRegistrationSsb/ssb/classSearch/" +
                "getTerms?&offset=1&max=2147483647"
    )

    json.decodeFromString<List<OptionResponse>>(response)
}

fun getOptions(type: String, term: String) = coroutineScope.async {
    val response = cachedRequest(
        "https://prodapps.isadm.oregonstate.edu/StudentRegistrationSsb/ssb/classSearch/" +
                "get_$type?term=$term&offset=1&max=2147483647"
    )

    json.decodeFromString<List<OptionResponse>>(response)
}

fun getSearch(search: Search) = coroutineScope.async {
    var offset = 0
    val classResponses = mutableListOf<ClassDataResponse>()
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

fun getLinks(crn: String, term: String) = coroutineScope.async {
    val response = cachedRequest(
        "https://prodapps.isadm.oregonstate.edu/StudentRegistrationSsb/ssb/searchResults/" +
                "fetchLinkedSections?term=$term&courseReferenceNumber=$crn"
    )

    json.decodeFromString<LinkedSearchResponse>(response)
}
