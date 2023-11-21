import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.time.Duration

// TODO: Use @SerialName more and to replace the snakeCase encoder

enum class Term {
    SPRING,
    SUMMER,
    FALL,
    WINTER
}

private val client = OkHttpClient()
private val cache = RequestResponseCache(File("./cache.json.gz"), Duration.ofDays(30), 1000L * 1000L * 1000L)
private val cacheLock = Semaphore(1)
// Setting this too high seems to cause the server not to respond
// Also the server seems to give up on to many responses to quickly
// TODO: Add rate limit
private val requestSemaphore = Semaphore(1)

// Make use year that is why this isn't part of the class
fun termToDBID(term: Term?): String {
    return when (term) {
        null -> "999999"
        Term.SPRING -> "202303"
        Term.SUMMER -> "202400"
        Term.FALL -> "202401"
        Term.WINTER -> "202402"
    }
}

suspend fun tryCache(key: Pair<String, String>): String? {
    cacheLock.acquire()
    val cached = cache.getOrNull(key)
    cacheLock.release()

    return cached
}

suspend fun cachedRequest(url: String, post: String? = null): String {
    val cacheKey = Pair(url, post ?: "")
    val cacheVal1 = tryCache(cacheKey)
    if (cacheVal1 != null) {
        return cacheVal1
    }

    val request = Request.Builder()

    request.url(url)
    if (post == null) {
        request.get()
    } else {
        request.post(post.toRequestBody())
    }

    if (!requestSemaphore.tryAcquire()) {
        requestSemaphore.acquire()

        val cacheVal2 = tryCache(cacheKey)
        if (cacheVal2 != null) {
            requestSemaphore.release()
            return cacheVal2
        }
    }

    val response = client.newCall(request.build()).execute().body!!.string()
    requestSemaphore.release()

    cacheLock.acquire()
    cache.set(cacheKey, response)
    cacheLock.release()

    return response
}

// TODO: Add argument for campus and move this over to the other api
// TODO: Look at https://prodapps.isadm.oregonstate.edu/StudentRegistrationSsb/ssb/classSearch/classSearch it also supports more categories
fun getSearch(search: String, term: Term?) = coroutineScope.async {
    // Maybe actually figure this out

    val field: String
    val value: String
    if (search.startsWith("BACC ")) {
        field = search.slice(5..<search.length)
        value = "Y"
    } else {
        field = if (' ' in search) {
            "alias"
        } else if (search.length == 5 && search.all { it.isDigit() }) {
            "crn"
        } else {
            "keyword"
        }

        value = search
    }

    val response = cachedRequest(
        "https://classes.oregonstate.edu/api/?page=fose&route=search",
        "{\"other\":{\"srcdb\":\"${termToDBID(term)}\"}," +
        "\"criteria\":[{\"field\":\"$field\",\"value\":\"$value\"}," +
        "{\"field\":\"camp\",\"value\":\"C\"}]}"
    )

    serverJson.decodeFromString<SearchResponse>(response)
}

// Term may not be necessary
// Server is dumb and sometimes crashes and returns a html page of an error
fun getLinked(crn: String, term: Term) = coroutineScope.async {
    val response = cachedRequest(
        "https://prodapps.isadm.oregonstate.edu/StudentRegistrationSsb/ssb/searchResults/" +
        "fetchLinkedSections?term=${termToDBID(term)}&courseReferenceNumber=$crn"
    )

    // TODO: Figure out how to catch internal exception
    try {
        // TODO: Figure out why there is a nested list (and then rename outer it)
        serverJson.decodeFromString<LinkedResponse>(response).linkedData.flatMap { datum -> datum.map { it.courseReferenceNumber } }
    } catch (_: Exception) {
        null
    }
}

// TODO: Get term from class data and make less lazy (revamp whole extra data stuff instead of being lazy)
fun getExtraData(classData: ClassData, term: Term) = coroutineScope.async {
    val response = cachedRequest(
        "https://classes.oregonstate.edu/api/?page=fose&route=details",
        "{\"group\":\"code:${classData.title}\",\"key\":\"crn:${classData.crn}\",\"srcdb\":\"${termToDBID(term)}\",\"matched\":\"crn:${classData.crn}\"}"
    )

    serverJson.decodeFromString<MoreDataResponse>(response)
}

fun getClass(crn: String) = coroutineScope.async {
    getSearch(crn, null).await().results.first { it.crn == crn }
}
