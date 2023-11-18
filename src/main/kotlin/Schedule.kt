import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlin.random.Random
import kotlin.time.Duration

// Maybe make all the stuff at the start better
// TODO: Remove all the repeated code for the callback
// TODO: Make it so you don't have to restart to continue downloading
fun genSchedule(classRequestData: List<Pair<List<String>, Double>>,
                term: Term,
                tries: Int,
                gradeFun: (List<Pair<ClassData, MoreDataResponse>>, List<List<ClassData>>) -> Double,
                workingCallback: (String, Double) -> Unit = { _, _ -> }): List<Schedule> {
    return runBlocking {
        // TODO: Check all the results Type and ID are correct
        val classRequestStrings = classRequestData.map { it.first }
        val classRequestDropChances = classRequestData.map { it.second }

        val callbackWriteLock = Semaphore(1)

        val total = classRequestStrings.sumOf { it.size }
        var progress = 0.0
        workingCallback("Downloading Classes", 0.0)
        val responseGroups = classRequestStrings
            .map { requests -> requests.map { getSearch(it, term) } }
            .map { responses ->
                responses.map {
                    async {
                        val value = it.await()

                        callbackWriteLock.acquire()
                        progress++
                        workingCallback("Downloading Classes", progress / total)
                        callbackWriteLock.release()

                        value
                    }.await()
                }
            }

        // TODO: Make the filter actually good
        val classGroups = responseGroups
            .map { group -> group.flatMap { response -> response.results.filter { it.schedule == "A" || it.schedule == "H" }.distinct() } }

        val oldClasses = responseGroups.flatMap { responses -> responses.flatMap { it.results } }
        val oldCrns = oldClasses.map { it.crn }

        val newClasses = mutableListOf<ClassData>()

        progress = 0.0
        workingCallback("Downloading Links", 0.0)
        val requirements = oldClasses
            .map { it.crn to getLinked(it.crn, term) }
            .associate {
                val value = it.first to it.second.await()

                for (linkedCrn in value.second) {
                    if (linkedCrn !in oldCrns) {
                        // Should move this into separate await thing
                        newClasses.add(getClass(linkedCrn).await())
                    }
                }

                callbackWriteLock.acquire()
                progress++
                workingCallback("Downloading Links", progress / oldClasses.size)
                callbackWriteLock.release()

                value
            }

        val classes = oldClasses + newClasses
        val crnToClass = classes.associateBy { it.crn }

        workingCallback("Crunching Numbers", 0.0)
        val rawSchedules = mutableListOf<List<ClassData>>()
        for (i in 0..<tries) {
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

            workingCallback("Crunching Numbers", (i + 1).toDouble() / tries)
        }

        val classesUsed = rawSchedules.flatten().distinct()

        progress = 0.0
        workingCallback("Downloading Extra Data", 0.0)
        val classDatumToMoreData = classesUsed
            .associateWith { getExtraData(it, term) }
            .mapValues {
                async {
                    val value = it.value.await()

                    callbackWriteLock.acquire()
                    progress++
                    workingCallback("Downloading Extra Data", progress / classesUsed.size)
                    callbackWriteLock.release()

                    value
                }.await()
            }

        val ungradedSchedules = rawSchedules.distinct().map { classData -> classData.map { it to classDatumToMoreData[it]!! } }

        progress = 0.0
        workingCallback("Grading Credits", 0.0)
        ungradedSchedules.map { data ->
                val value = Schedule(data, gradeFun(data, classGroups))

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

fun genGradeFun(groupWeights: List<Double>,
                breaksAndWeights: List<Pair<Break, Double>>,
                creditWeight: Double,
                backToBackCutoff: Duration,
                backToBackWeight: Double): (List<Pair<ClassData, MoreDataResponse>>, List<List<ClassData>>) -> Double {
    return { classData, classGroups ->
        var grade = classData.sumOf { it.second.credits } * creditWeight
        val classTimes = classData.flatMap { it.first.meetingTimes }

        for ((currBreak, weight) in breaksAndWeights) {
            grade -= weight * countIntersects(currBreak, classTimes)
        }

        val backToBackCount = classTimes.count { classTime ->
            val backToBackRange = classTime.endTime..(classTime.endTime + backToBackCutoff)
            classTimes.any { classTime.meetDay == it.meetDay && it.startTime in backToBackRange }
        }
        grade += backToBackCount * backToBackWeight

        for (classDatum in classData) {
            val index = classGroups.indexOfFirst { classDatum.first in it }
            if (index != -1) {
                grade += groupWeights[index]
            }
        }

        grade
    }
}
