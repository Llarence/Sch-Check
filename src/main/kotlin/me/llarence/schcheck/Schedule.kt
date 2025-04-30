package me.llarence.schcheck

import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

@Serializable
data class MeetTime(val start: DayTime, val end: DayTime, val day: DayOfWeek) {
    fun intersects(other: MeetTime): Boolean {
        if (day == other.day) {
            return !(end.inMinutes < other.start.inMinutes || other.end.inMinutes < start.inMinutes)
        }

        return false
    }
}

// If links is null it is unknown
// Why can credits be null
@Serializable
data class ClassData(val crn: String, val title: String, val meetTimes: List<MeetTime>, val credits: Int?, val links: List<ClassData>?)

suspend fun convertResponse(classDataResponse: ClassDataResponse, link: Boolean = false): ClassData {
    val meetTimes = mutableListOf<MeetTime>()

    for (meetingFaculty in classDataResponse.meetingsFaculty) {
        if (meetingFaculty.meetingTime.beginTime != null && meetingFaculty.meetingTime.endTime != null) {
            if (meetingFaculty.meetingTime.monday) {
                meetTimes.add(
                    MeetTime(meetingFaculty.meetingTime.beginTime,
                    meetingFaculty.meetingTime.endTime,
                    DayOfWeek.MONDAY)
                )
            }
            if (meetingFaculty.meetingTime.tuesday) {
                meetTimes.add(
                    MeetTime(meetingFaculty.meetingTime.beginTime,
                    meetingFaculty.meetingTime.endTime,
                    DayOfWeek.TUESDAY)
                )
            }
            if (meetingFaculty.meetingTime.wednesday) {
                meetTimes.add(
                    MeetTime(meetingFaculty.meetingTime.beginTime,
                    meetingFaculty.meetingTime.endTime,
                    DayOfWeek.WEDNESDAY)
                )
            }
            if (meetingFaculty.meetingTime.thursday) {
                meetTimes.add(
                    MeetTime(meetingFaculty.meetingTime.beginTime,
                    meetingFaculty.meetingTime.endTime,
                    DayOfWeek.THURSDAY)
                )
            }
            if (meetingFaculty.meetingTime.friday) {
                meetTimes.add(
                    MeetTime(meetingFaculty.meetingTime.beginTime,
                    meetingFaculty.meetingTime.endTime,
                    DayOfWeek.FRIDAY)
                )
            }
            if (meetingFaculty.meetingTime.saturday) {
                meetTimes.add(
                    MeetTime(meetingFaculty.meetingTime.beginTime,
                    meetingFaculty.meetingTime.endTime,
                    DayOfWeek.SATURDAY)
                )
            }
            if (meetingFaculty.meetingTime.sunday) {
                meetTimes.add(
                    MeetTime(meetingFaculty.meetingTime.beginTime,
                    meetingFaculty.meetingTime.endTime,
                    DayOfWeek.SUNDAY)
                )
            }
        }
    }

    // TODO: Figure out how credit hours work
    val credits = classDataResponse.creditHours ?: classDataResponse.creditHourLow!!

    // TODO: Sometimes there can be identical copies of a MeetTime such as with 56169 Spring Term 2024
    return if (link) {
        ClassData(classDataResponse.courseReferenceNumber,
            classDataResponse.courseTitle,
            meetTimes.distinct(),
            credits,
            null)
    } else {
        val linksResponse = getLinks(classDataResponse.courseReferenceNumber, classDataResponse.term)
        ClassData(classDataResponse.courseReferenceNumber,
            classDataResponse.courseTitle,
            meetTimes.distinct(),
            credits,
            // I don't know why there is an outer list it seems to only have one element
            linksResponse.linkedData.flatMap { linkedData -> linkedData.map { convertResponse(it, true) } })
    }
}

// TODO: Make the set part of
fun genSchedules(classGroups: List<List<ClassData>>, tries: Int, skipChance: Double): List<List<ClassData>> {
    val schedules = mutableSetOf<Set<ClassData>>()
    for (i in 0..<tries) {
        val scheduleMeetTimes = mutableListOf<MeetTime>()
        val schedule = mutableSetOf<ClassData>()

        for (classGroup in classGroups.shuffled()) {
            if (classGroup.isNotEmpty() && Random.nextDouble() > skipChance) {
                val classData = classGroup.random()

                if (!scheduleMeetTimes.any { meetTime -> classData.meetTimes.any { meetTime.intersects(it) } }) {
                    scheduleMeetTimes.addAll(classData.meetTimes)
                    schedule.add(classData)
                }
            }
        }

        if (!addLinks(schedule, scheduleMeetTimes)) {
            continue
        }

        if (schedule.isNotEmpty()) {
            schedules.add(schedule)
        }
    }

    return schedules.map { it.toList() }
}

fun addLinks(schedule: MutableSet<ClassData>, scheduleMeetTimes: MutableList<MeetTime>): Boolean {
    val links = mutableSetOf<ClassData>()

    // Unclear if the shuffled is necessary
    for (classData in schedule.shuffled()) {
        if (classData.links!!.isNotEmpty()) {
            var found = false
            for (linkData in classData.links.shuffled()) {
                if (!scheduleMeetTimes.any { meetTime -> linkData.meetTimes.any { meetTime.intersects(it) } }) {
                    scheduleMeetTimes.addAll(linkData.meetTimes)
                    links.add(linkData)

                    found = true
                    break
                }
            }

            if (!found) {
                return false
            }
        }
    }

    schedule.addAll(links)
    return true
}

fun adjacent(first: ClassData, second: ClassData, deltaMinutes: Int): Boolean {
    for (firstMeetTime in first.meetTimes) {
        for (secondMeetTime in second.meetTimes) {
            if (firstMeetTime.day == secondMeetTime.day) {
                if (firstMeetTime.start.inMinutes - secondMeetTime.end.inMinutes <= deltaMinutes ||
                    secondMeetTime.start.inMinutes - firstMeetTime.end.inMinutes <= deltaMinutes) {
                    return true
                }
            }
        }
    }

    return false
}

fun valueSchedule(schedule: List<ClassData>, rankingArguments: ScheduleRankingArguments): Double {
    var value = 0.0

    for (i in schedule.indices) {
        val classData = schedule[i]

        for (meetTime in classData.meetTimes) {
            val disStart = abs(meetTime.start.inMinutes - rankingArguments.targetTime.inMinutes)
            val disEnd = abs(meetTime.end.inMinutes - rankingArguments.targetTime.inMinutes)

            value -= rankingArguments.timeDistanceValue * min(disStart, disEnd)
        }

        for (j in 0..<i) {
            if (adjacent(classData, schedule[j], 15)) {
                value += rankingArguments.adjacentValue
            }
        }
    }

    // Why can credits be null
    value += schedule.sumOf { (it.credits ?: 0) * rankingArguments.creditValue }

    return value
}
