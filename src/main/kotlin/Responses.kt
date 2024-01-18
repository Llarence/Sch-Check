import kotlinx.serialization.*
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json

val json = Json { ignoreUnknownKeys = true }

@Serializable
data class Option(val code: String, val description: String)

object DayTimeSerializer : KSerializer<DayTime> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("DayTimeSerializer", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: DayTime) {
        val minutes = (value.minute - (value.hour * 60)).toString().padStart(2, '0')
        encoder.encodeString("${value.hour}$minutes")
    }

    override fun deserialize(decoder: Decoder): DayTime {
        val string = decoder.decodeString().padStart(4, '0')
        return DayTime(string.substring(2, 4).toInt(), string.substring(0, 2).toInt())
    }
}

@Serializable
data class MeetTime(val monday: Boolean,
                    val tuesday: Boolean,
                    val wednesday: Boolean,
                    val thursday: Boolean,
                    val friday: Boolean,
                    val saturday: Boolean,
                    val sunday: Boolean,
                    @Serializable(with = DayTimeSerializer::class) val beginTime: DayTime?,
                    @Serializable(with = DayTimeSerializer::class) val endTime: DayTime?)

@Serializable
data class MeetingFaculty(val meetingTime: MeetTime)

// It is not clear if there can be multiple meetingsFaculty
@Serializable
data class ClassData(@SerialName("courseReferenceNumber") val crn: String, val meetingsFaculty: List<MeetingFaculty>)

@Serializable
data class SearchResponse(val totalCount: Int, val data: List<ClassData>)
