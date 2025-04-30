package me.llarence.schcheck

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
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
import java.util.concurrent.Semaphore

private val cacheJson = Json { allowStructuredMapKeys = true }

@Serializable(with = SerializableInstantSerializer::class)
class SerializableInstant(val instant: Instant)

object SerializableInstantSerializer : KSerializer<SerializableInstant> {
    override val descriptor = PrimitiveSerialDescriptor("SerializableInstantSerializer", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): SerializableInstant {
        return SerializableInstant(Instant.parse(decoder.decodeString()))
    }

    override fun serialize(encoder: Encoder, value: SerializableInstant) {
        encoder.encodeString(value.instant.toString())
    }
}

// Added gzip just cause
// Maybe should move the cache out of ram
// MaxSize is only an estimate
// TODO: Improve the saving to not redo the whole thing everytime
// TODO: Maybe use serializers like in Saver to make it not only strings
@OptIn(ExperimentalSerializationApi::class)
class RequestResponseCache(private val file: File,
                           private val retainDuration: TemporalAmount,
                           private val maxSize: Long) {
    private val lock = Semaphore(1)

    init {
        if (!file.exists()) {
            val folder = file.parentFile
            if (!folder.exists()) {
                if (!folder.mkdirs()) {
                    throw IOException()
                }
            }

            if (!file.createNewFile()) {
                throw IOException()
            }
        }
    }

    private var data: MutableMap<String, Pair<String, SerializableInstant>>
    private var size = 0L

    init {
        val fileStream = FileInputStream(file)

        if (fileStream.available() == 0) {
            data = mutableMapOf()
        } else {
            data = try {
                val gzipStream = GZIPInputStream(fileStream)
                val read: MutableMap<String, Pair<String, SerializableInstant>> = cacheJson.decodeFromStream(gzipStream)
                gzipStream.close()

                read
            } catch (err: Exception) {
                mutableMapOf()
            }
        }
        fileStream.close()

        calcSize()
        save()
    }

    // Could include the size of the instant
    private fun getEntrySize(entry: MutableMap.MutableEntry<String, Pair<String, SerializableInstant>>): Long {
        return (entry.key.length + entry.value.first.length).toLong()
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
    fun set(key: String, value: String) {
        lock.acquire()
        data[key] = Pair(value, SerializableInstant(Instant.now()))
        lock.release()
    }

    fun getOrNull(string: String): String? {
        lock.acquire()

        val datum = data[string]
        val output = if (datum != null) {
            if (datum.second.instant + retainDuration < Instant.now()) {
                data.remove(string)
                null
            } else {
                datum.first
            }
        } else {
            null
        }

        lock.release()
        return output
    }

    fun save() {
        pruneCache()

        val fileStream = FileOutputStream(file)
        val gzipStream = GZIPOutputStream(fileStream)
        cacheJson.encodeToStream(data, gzipStream)
        gzipStream.close()
        fileStream.close()
    }
}
