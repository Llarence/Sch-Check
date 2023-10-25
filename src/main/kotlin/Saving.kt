import kotlinx.serialization.*
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
class Saver<T>(private val directory: File, private val serializer: SerializationStrategy<T>, private val deserializer: DeserializationStrategy<T>) {
    init {
        if (!directory.isDirectory) {
            if (!directory.mkdirs()) {
                throw IOException()
            }
        }
    }

    companion object {
        inline fun <reified T> create(directory: File): Saver<T> {
            val serializer = serializer<T>()
            return Saver(directory, serializer, serializer)
        }
    }

    fun save(name: String, value: T) {
        val file = directory.resolve("$name.json.gz")

        if (!file.exists()) {
            if (!file.createNewFile()) {
                throw IOException()
            }
        }

        val fileStream = FileOutputStream(file)
        val gzipStream = GZIPOutputStream(fileStream)
        Json.encodeToStream(serializer, value, gzipStream)
        gzipStream.close()
    }

    fun load(name: String): T? {
        val file = directory.resolve("$name.json.gz")

        if (!file.exists()) {
            return null
        }

        val fileStream = FileInputStream(file)
        val gzipStream = GZIPInputStream(fileStream)
        val value = Json.decodeFromStream(deserializer, gzipStream)
        gzipStream.close()

        return value
    }

    fun getSaveNames(): List<String> {
        return directory.listFiles()!!.map { it.name.drop(8) }
    }
}
