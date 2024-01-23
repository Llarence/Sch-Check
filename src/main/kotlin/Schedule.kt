import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import kotlin.random.Random

@Serializable
data class MeetTime(val start: DayTime, val end: DayTime, val day: DayOfWeek) {
    fun intersects(other: MeetTime): Boolean {
        if (day == other.day) {
            if (start.inMinutes > other.end.inMinutes || other.end.inMinutes > start.inMinutes) {
                return false
            }
        }

        return true
    }
}

// If links is null it is unknown
@Serializable
data class ClassData(val crn: String, val title: String, val meetTimes: List<MeetTime>, val links: List<ClassData>?)

suspend fun convertResponse(classDataResponse: ClassDataResponse, link: Boolean = false): ClassData {
    val meetTimes = mutableListOf<MeetTime>()

    for (meetingFaculty in classDataResponse.meetingsFaculty) {
        if (meetingFaculty.meetingTime.beginTime != null && meetingFaculty.meetingTime.endTime != null) {
            if (meetingFaculty.meetingTime.monday) {
                meetTimes.add(MeetTime(meetingFaculty.meetingTime.beginTime,
                    meetingFaculty.meetingTime.endTime,
                    DayOfWeek.MONDAY))
            }
            if (meetingFaculty.meetingTime.tuesday) {
                meetTimes.add(MeetTime(meetingFaculty.meetingTime.beginTime,
                    meetingFaculty.meetingTime.endTime,
                    DayOfWeek.TUESDAY))
            }
            if (meetingFaculty.meetingTime.wednesday) {
                meetTimes.add(MeetTime(meetingFaculty.meetingTime.beginTime,
                    meetingFaculty.meetingTime.endTime,
                    DayOfWeek.WEDNESDAY))
            }
            if (meetingFaculty.meetingTime.thursday) {
                meetTimes.add(MeetTime(meetingFaculty.meetingTime.beginTime,
                    meetingFaculty.meetingTime.endTime,
                    DayOfWeek.THURSDAY))
            }
            if (meetingFaculty.meetingTime.friday) {
                meetTimes.add(MeetTime(meetingFaculty.meetingTime.beginTime,
                    meetingFaculty.meetingTime.endTime,
                    DayOfWeek.FRIDAY))
            }
            if (meetingFaculty.meetingTime.saturday) {
                meetTimes.add(MeetTime(meetingFaculty.meetingTime.beginTime,
                    meetingFaculty.meetingTime.endTime,
                    DayOfWeek.SATURDAY))
            }
            if (meetingFaculty.meetingTime.sunday) {
                meetTimes.add(MeetTime(meetingFaculty.meetingTime.beginTime,
                    meetingFaculty.meetingTime.endTime,
                    DayOfWeek.SUNDAY))
            }
        }
    }

    return if (link) {
        ClassData(classDataResponse.courseReferenceNumber,
            classDataResponse.courseTitle,
            meetTimes,
            null)
    } else {
        val linksResponse = getLinks(classDataResponse.courseReferenceNumber, classDataResponse.term)
        ClassData(classDataResponse.courseReferenceNumber,
            classDataResponse.courseTitle,
            meetTimes,
            // I don't know why there is an outer list it seems to only have one element
            linksResponse.linkedData.flatMap { linkedData -> linkedData.map { convertResponse(it, true) } })
    }
}

fun genSchedules(classGroups: List<List<ClassData>>, tries: Int, skipChance: Double): List<List<ClassData>> {
    val schedules = mutableListOf<List<ClassData>>()
    for (i in 0..<tries) {
        val scheduleMeetTimes = mutableListOf<MeetTime>()
        val schedule = mutableSetOf<ClassData>()

        for (classGroup in classGroups.shuffled()) {
            if (Random.nextDouble() > skipChance) {
                val classData = classGroup.random()

                if (!scheduleMeetTimes.any { meetTime -> classData.meetTimes.any { meetTime.intersects(it) } }) {
                    scheduleMeetTimes.addAll(classData.meetTimes)
                    schedule.add(classData)
                }
            }
        }

        val links = mutableSetOf<ClassData>()
        for (classData in schedule) {
            if (classData.links!!.isNotEmpty()) {
                links.add(classData.links.random())
            }
        }

        val fullSchedule = schedule + links
        if (fullSchedule.isNotEmpty()) {
            schedules.add(fullSchedule.toList())
        }
    }

    return schedules
}
