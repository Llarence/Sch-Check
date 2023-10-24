import kotlinx.serialization.*
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

val serverJson = Json { ignoreUnknownKeys = true }
@OptIn(ExperimentalSerializationApi::class)
val snakeServerJson = Json { ignoreUnknownKeys = true; namingStrategy = JsonNamingStrategy.SnakeCase }

object MeetTimeDurationSerializer : KSerializer<Duration> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("MeetTimeDurationSerializer", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Duration) {
        val minutes = (value.inWholeMinutes - (value.inWholeHours * 60)).toString().padStart(2, '0')
        encoder.encodeString("${value.inWholeHours}$minutes")
    }

    override fun deserialize(decoder: Decoder): Duration {
        val durationString = decoder.decodeString().padStart(4, '0')
        return durationString.substring(0, 2).toInt().hours + durationString.substring(2, 4).toInt().minutes
    }
}

@Serializable
data class MeetTime(val meetDay: Int, @Serializable(with = MeetTimeDurationSerializer::class) val startTime: Duration, @Serializable(with = MeetTimeDurationSerializer::class) val endTime: Duration)

object MeetTimesListSerializer : KSerializer<List<MeetTime>> {
    override val descriptor = PrimitiveSerialDescriptor("MeetTimesResponseSerializer", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: List<MeetTime>) {
        encoder.encodeString(snakeServerJson.encodeToString(value))
    }

    override fun deserialize(decoder: Decoder): List<MeetTime> {
        return snakeServerJson.decodeFromString<List<MeetTime>>(decoder.decodeString())
    }
}

// TODO: Make camp and enums (camp can be multiple though) and add term to it
@Serializable
data class ClassData(val title: String, @SerialName("camp") val campus: String, @SerialName("schd") val schedule: String, val crn: String, @Serializable(with = MeetTimesListSerializer::class) val meetingTimes: List<MeetTime>)

@Serializable
data class SearchResponse(val results: List<ClassData>)

@Serializable
data class LinkedClass(val courseReferenceNumber: String)

@Serializable
data class LinkedResponse(val linkedData: List<List<LinkedClass>>)

// Maybe there is a better way to do this
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

@Serializable
data class MoreDataResponse(@SerialName("hours_html") val credits: Int)

@Serializable
data class Schedule(val classData: List<ClassData>, val credits: Int, val grade: Double)
