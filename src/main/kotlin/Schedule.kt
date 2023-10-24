import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlin.random.Random

// Maybe make all the stuff at the start better
// TODO: Remove all the repeated code for the callback
// TODO: Make it so you don't have to restart to continue downloading
fun genSchedule(classRequestData: List<Pair<List<String>, Double>>,
                term: Term,
                tries: Int,
                gradeFun: (List<ClassData>, credits: Int) -> Double,
                workingCallback: (String, Double) -> Unit = { _, _ -> }): List<Schedule> {
    return runBlocking {
        // TODO: Check all the results Type and ID are correct
        val classRequestStrings = classRequestData.map { it.first }
        val classRequestDropChances = classRequestData.map { it.second }

        val callbackWriteLock = Semaphore(1)
        var progress = 0.0

        val total = classRequestStrings.sumOf { it.size }
        workingCallback("Downloading Classes", 0.0)
        val responseGroups = classRequestStrings
            .map { requests -> requests.map { getSearch(it, term) } }
            .map { responses -> responses.map {
                async {
                    val value = it.await()

                    callbackWriteLock.acquire()
                    progress++
                    workingCallback("Downloading", progress / total)
                    callbackWriteLock.release()

                    value
                }.await()
            } }

        // TODO: Make the filter actually good
        val classGroups = responseGroups
            .map { group -> group.flatMap { response -> response.results.filter { it.schedule == "A" || it.schedule == "H" }.distinct() } }

        val classes = responseGroups.flatMap { responses -> responses.flatMap { it.results } }
        val crnToClass = classes.associateBy { it.crn }

        progress = 0.0

        workingCallback("Downloading Links", 0.0)
        val requirements = classes
            .map { it.crn to getLinked(it.crn, term) }
            .associate {
                val value = Pair(it.first, it.second.await())

                callbackWriteLock.acquire()
                progress++
                workingCallback("Downloading Links", progress / classes.size)
                callbackWriteLock.release()

                value
            }

        workingCallback("Crunching Numbers", 0.0)
        val rawSchedules = mutableListOf<List<ClassData>>()
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
                rawSchedules.add(randomSchedule)
            }

            workingCallback("Crunching Numbers", i.toDouble() / tries)
        }

        progress = 0.0

        workingCallback("Downloading Credits", 0.0)
        val ungradedSchedules = rawSchedules
            .map { data -> Pair(data, data.map { getHours(it, term) }) }
            .map { data ->
                async {
                    val value = Pair(data.first, data.second.sumOf { it.await() })

                    callbackWriteLock.acquire()
                    progress++
                    workingCallback("Downloading Credits", progress / rawSchedules.size)
                    callbackWriteLock.release()

                    value
                }.await()
            }

        workingCallback("Grading Credits", 0.0)
        ungradedSchedules.map { data ->
                val value = Schedule(data.first, data.second, gradeFun(data.first, data.second))

                progress++
                workingCallback("Grading Schedules", progress / rawSchedules.size)

                value
            }
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
