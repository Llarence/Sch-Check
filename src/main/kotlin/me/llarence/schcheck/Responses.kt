package me.llarence.schcheck

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.*

val json = Json { ignoreUnknownKeys = true }

@Serializable
data class OptionResponse(val code: String, val description: String)

object DayTimeSerializer : KSerializer<DayTime> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("DayTimeSerializer", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: DayTime) {
        encoder.encodeString("${value.hour}${value.minute}")
    }

    override fun deserialize(decoder: Decoder): DayTime {
        val string = decoder.decodeString().padStart(4, '0')
        return DayTime(string.substring(0, 2).toInt(), string.substring(2, 4).toInt())
    }
}

object DateSerializer : KSerializer<Date> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("DateSerializer", PrimitiveKind.STRING)

    // They use month day year for some reason
    val formatter = SimpleDateFormat("MM/dd/yyyy")

    override fun serialize(encoder: Encoder, value: Date) {
        encoder.encodeString(formatter.format(value))
    }

    override fun deserialize(decoder: Decoder): Date {
        return formatter.parse(decoder.decodeString())
    }
}

@Serializable
data class MeetingTimeResponse(val monday: Boolean,
                               val tuesday: Boolean,
                               val wednesday: Boolean,
                               val thursday: Boolean,
                               val friday: Boolean,
                               val saturday: Boolean,
                               val sunday: Boolean,
                               @Serializable(with=DayTimeSerializer::class) val beginTime: DayTime?,
                               @Serializable(with=DayTimeSerializer::class) val endTime: DayTime?,
                               @Serializable(with=DateSerializer::class) val startDate: Date,
                               @Serializable(with=DateSerializer::class) val endDate: Date)

@Serializable
data class MeetingFacultyResponse(val meetingTime: MeetingTimeResponse)

// TODO: Figure out what multiple meetingsFaculties would mean
//  selecting term and hitting search shows some with multiple meetingsFaculties
@Serializable
data class ClassDataResponse(val courseReferenceNumber: String,
                             val seatsAvailable: Int,
                             val courseTitle: String,
                             val meetingsFaculty: List<MeetingFacultyResponse>,
                             val creditHours: Int?,
                             val creditHourLow: Int?,
                             val term: String)

@Serializable
data class SearchResponse(val totalCount: Int, val data: List<ClassDataResponse>)

// Not sure why the double list the first list only seems to have one element
@Serializable
data class LinkedSearchResponse(val linkedData: List<List<ClassDataResponse>>)
