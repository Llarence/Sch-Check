import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

// Added gzip just cause
@OptIn(ExperimentalSerializationApi::class)
class Saver(private val directory: File) {
    init {
        if (!directory.isDirectory) {
            if (!directory.mkdirs()) {
                throw IOException()
            }
        }
    }

    fun save(name: String, schedule: Schedule) {
        val file = directory.resolve("$name.json.gz")

        if (!file.exists()) {
            if (!file.createNewFile()) {
                throw IOException()
            }
        }

        val fileStream = FileOutputStream(file)
        val gzipStream = GZIPOutputStream(fileStream)
        Json.encodeToStream(schedule, gzipStream)
        gzipStream.close()
    }

    fun load(name: String): Schedule? {
        val file = directory.resolve("$name.json.gz")

        if (!file.exists()) {
            return null
        }

        val fileStream = FileInputStream(file)
        val gzipStream = GZIPInputStream(fileStream)
        val schedule = Json.decodeFromStream<Schedule>(gzipStream)
        gzipStream.close()

        return schedule
    }

    fun getSaveNames(): List<String> {
        return directory.listFiles()!!.map { it.name.drop(8) }
    }
}
