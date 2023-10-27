import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.time.Instant
import java.time.temporal.TemporalAmount
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

private val cacheJson = Json { allowStructuredMapKeys = true }

// Added gzip just cause
// Maybe should move the cache out of ram
// MaxSize is only an estimate
// TODO: Improve the saving to not redo the whole thing everytime
@OptIn(ExperimentalSerializationApi::class)
class RequestResponseCache(private val file: File, private val retainDuration: TemporalAmount, private val maxSize: Long) {
    init {
        if (!file.exists()) {
            if (!file.createNewFile()) {
                throw IOException()
            }
        }
    }

    private val data: MutableMap<Pair<String, String?>, Pair<String, SerializableInstant>>
    private var size = 0L

    init {
        val fileStream = FileInputStream(file)

        if (fileStream.available() == 0) {
            data = mutableMapOf()
        } else {
            val gzipStream = GZIPInputStream(fileStream)
            data = cacheJson.decodeFromStream(gzipStream)
            gzipStream.close()
        }

        calcSize()
        save()
    }

    private fun getEntrySize(entry: MutableMap.MutableEntry<Pair<String, String?>, Pair<String, SerializableInstant>>): Long {
        return entry.key.first.length.toLong() + (entry.key.second?.length ?: 0) + entry.value.first.length
    }

    private fun calcSize() {
        size = 0

        for (entry in data.entries) {
            size += getEntrySize(entry)
        }
    }

    private fun pruneCache() {
        if (size > maxSize) {
            val sortedEntries = data.entries.sortedBy { it.value.second.instant }

            for (entry in sortedEntries) {
                size -= getEntrySize(entry)
                data.remove(entry.key)

                if (size <= maxSize) {
                    break
                }
            }
        }
    }

    // A huge datum could just be instantly deleted if it is over the maxSize
    fun set(key: Pair<String, String?>, value: String) {
        data[key] = Pair(value, SerializableInstant(Instant.now()))

        // This is probably really inefficient
        save()
    }

    fun getOrNull(string: Pair<String, String?>): String? {
        val datum = data[string]
        if (datum != null) {
            return if (datum.second.instant + retainDuration < Instant.now()) {
                data.remove(string)
                null
            } else {
                datum.first
            }
        }

        return null
    }

    private fun save() {
        pruneCache()

        val fileStream = FileOutputStream(file)
        val gzipStream = GZIPOutputStream(fileStream)
        cacheJson.encodeToStream(data, gzipStream)
        gzipStream.close()
    }
}
