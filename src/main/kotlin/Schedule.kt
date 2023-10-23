import kotlinx.coroutines.runBlocking
import kotlin.random.Random

data class Schedule(val classData: List<ClassData>, val credits: Int, val grade: Double)

// Maybe make all the stuff at the start better
fun genSchedule(classRequestData: List<Pair<List<String>, Double>>, term: Term, tries: Int, gradeFun: (List<ClassData>, credits: Int) -> Double): List<Schedule> {
    return runBlocking {
        // TODO: Check all the results Type and ID are correct
        val classRequestStrings = classRequestData.map { it.first }
        val classRequestDropChances = classRequestData.map { it.second }
        val responseGroups = classRequestStrings
            .map { requests -> requests.map { getSearch(it, term) } }
            .map { responses -> responses.map { it.await() } }
        // TODO: Make the filter actually good
        val classGroups = responseGroups
            .map { group -> group.flatMap { response -> response.results.filter { it.schedule == "A" || it.schedule == "H" }.distinct() } }

        val classes = responseGroups.flatMap { responses -> responses.flatMap { it.results } }
        val crnToClass = classes.associateBy { it.crn }

        val requirements = classes.map { it.crn to getLinked(it.crn, term) }.associate { it.first to it.second.await() }

        val schedules = mutableListOf<List<ClassData>>()
        for (i in 0..<tries) {
            val randomSchedule = mutableListOf<ClassData>()
            for (j in classRequestData.indices) {
                val classGroup = classGroups[j]
                if (Random.nextDouble() >= classRequestDropChances[j]) {
                    val randomClass = classGroup.random()
                    randomSchedule.add(randomClass)

                    val randomRequirement = requirements[randomClass.crn]!!.randomOrNull()
                    if (randomRequirement != null) {
                        randomSchedule.add(crnToClass[randomRequirement]!!)
                    }
                }
            }

            if (checkValidSchedule(randomSchedule)) {
                schedules.add(randomSchedule)
            }
        }

        schedules
            .map { data -> Pair(data, data.map { getHours(it, term) }) }
            .map { data -> Pair(data.first, data.second.sumOf { it.await() }) }
            .map { data -> Schedule(data.first, data.second, gradeFun(data.first, data.second)) }
            .sortedBy { -it.grade }
    }
}

fun checkValidSchedule(schedule: List<ClassData>): Boolean {
    if (schedule.distinctBy { it.crn }.size < schedule.size) {
        return false
    }

    return checkValidTimes(schedule.flatMap { it.meetingTimes })
}

fun checkValidTimes(times: List<MeetTime>): Boolean {
    for (i in times.indices) {
        for (j in (i + 1)..<times.size) {
            if (checkIntersect(times[i], times[j])) {
                return false
            }
        }
    }

    return true
}

fun checkIntersects(time: MeetTime, times: List<MeetTime>): Boolean {
    for (other in times) {
        if (checkIntersect(time, other)) {
            return true
        }
    }

    return false
}

fun checkIntersect(time1: MeetTime, time2: MeetTime): Boolean {
    if (time1.meetDay == time2.meetDay) {
        if (time1.endTime > time2.startTime && time2.endTime > time1.startTime) {
            return true
        }
    }

    return false
}

fun genScheduleGrader(breaks: List<MeetTime>, breakVal: Double, creditVal: Double): (List<ClassData>, Int) -> Double {
    return { classes, credits ->
        var grade = credits * creditVal
        val classTimes = classes.flatMap { it.meetingTimes }

        for (currBreak in breaks) {
            if (checkIntersects(currBreak, classTimes)) {
                grade -= breakVal
            }
        }

        grade
    }
}
