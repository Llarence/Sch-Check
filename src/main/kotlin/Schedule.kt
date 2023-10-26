import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlin.math.max
import kotlin.random.Random
import kotlin.time.Duration

// Maybe make all the stuff at the start better
// TODO: Remove all the repeated code for the callback
// TODO: Make it so you don't have to restart to continue downloading
fun genSchedule(classRequestData: List<Pair<List<String>, Double>>,
                term: Term,
                tries: Int,
                maxSchedules: Int,
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
                    workingCallback("Downloading Classes", progress / total)
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
                val value = it.first to it.second.await()

                callbackWriteLock.acquire()
                progress++
                workingCallback("Downloading Links", progress / classes.size)
                callbackWriteLock.release()

                value
            }

        workingCallback("Crunching Numbers", 0.0)
        val rawSchedules = mutableListOf<List<ClassData>>()
        for (i in 0..<tries) {
            if (rawSchedules.size >= maxSchedules) {
                break
            }

            val randomSchedule = mutableListOf<ClassData>()
            for (j in classRequestData.indices) {
                val classGroup = classGroups[j]
                // TODO: Remove from lists instead of check isNotEmpty each time
                if (classGroup.isNotEmpty() && Random.nextDouble() >= classRequestDropChances[j]) {
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

            workingCallback("Crunching Numbers", max(rawSchedules.size.toDouble() / maxSchedules, (i + 1).toDouble() / tries))
        }

        progress = 0.0
        workingCallback("Downloading Credits", 0.0)
        val classesUsed = rawSchedules.flatten().distinct()
        val classDataToCredits = classesUsed
            .associateWith { getHours(it, term) }
            .mapValues {
                async {
                    val value = it.value.await()

                    callbackWriteLock.acquire()
                    progress++
                    workingCallback("Downloading Credits", progress / classesUsed.size)
                    callbackWriteLock.release()

                    value
                }.await()
            }

        val ungradedSchedules = rawSchedules.map { classData -> Pair(classData, classData.sumOf { classDataToCredits[it]!! }) }

        progress = 0.0
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

fun checkIntersect(time1: MeetTime, time2: MeetTime): Boolean {
    if (time1.meetDay == time2.meetDay) {
        if (time1.endTime > time2.startTime && time2.endTime > time1.startTime) {
            return true
        }
    }

    return false
}

fun countIntersects(currBreak: Break, times: List<MeetTime>): Int {
    var total = 0

    for (other in times) {
        if (checkIntersect(currBreak, other)) {
            total++
        }
    }

    return total
}

fun checkIntersect(currBreak: Break, time: MeetTime): Boolean {
    if (time.meetDay in currBreak.meetDays) {
        if (currBreak.endTime > time.startTime && time.endTime > currBreak.startTime) {
            return true
        }
    }

    return false
}

fun genGradeFun(breaksAndWeights: List<Pair<Break, Double>>, creditWeight: Double, backToBackCutoff: Duration, backToBackWeight: Double): (List<ClassData>, Int) -> Double {
    return { classes, credits ->
        var grade = credits * creditWeight
        val classTimes = classes.flatMap { it.meetingTimes }

        for ((currBreak, weight) in breaksAndWeights) {
            grade -= weight * countIntersects(currBreak, classTimes)
        }

        val backToBackCount = classTimes.count { classTime ->
            val backToBackRange = classTime.endTime..(classTime.endTime + backToBackCutoff)
            classTimes.any { classTime.meetDay == it.meetDay && it.startTime in backToBackRange }
        }
        grade += backToBackCount * backToBackWeight

        grade
    }
}
