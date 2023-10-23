import com.calendarfx.model.Calendar
import com.calendarfx.model.CalendarSource
import com.calendarfx.model.Entry
import com.calendarfx.model.Interval
import com.calendarfx.view.CalendarView
import javafx.application.Application
import javafx.application.Application.launch
import javafx.application.Platform
import javafx.event.EventHandler
import javafx.scene.Scene
import javafx.stage.Stage
import javafx.stage.WindowEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.javafx.JavaFx
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.min
import kotlin.system.exitProcess

val coroutineScope = CoroutineScope(Dispatchers.Default)
val fxScope = CoroutineScope(Dispatchers.JavaFx)

fun LocalDate.withWeekday(day: Int): LocalDate {
    return this.plusDays(day.toLong() - this.dayOfWeek.value)
}

class App : Application() {
    override fun start(stage: Stage) {
        val deferredSchedules = coroutineScope.async {
            genSchedule(listOf(
                Pair(listOf("MTH 311", "MTH 343", "MTH 351"), 0.1),
                Pair(listOf("MTH 311", "MTH 343", "MTH 351"), 0.1),
                Pair(listOf("COMM 114"), 0.1),
                Pair(listOf("ENGR 103"), 0.1),
                Pair(listOf("PH 211"), 0.1),
                Pair(listOf("ANTH 284", "BI 102", "BI 103", "BI 204"), 0.1),
                Pair(listOf("ED 216", "ED 219", "ENG 220", "ENG 260"), 0.1),
                Pair(listOf("ATS 342", "GEO 308", "GEOG 300", "H 312"), 0.1),
                Pair(listOf("ANTH 330", "ART 367", "ES 319", "FES 485"), 0.1)
            ), Term.WINTER, 4000, genScheduleGrader(listOf(), 0.0, 1.0))
        }

        val calendarView = CalendarView()
        val source = CalendarSource("Schedule")

        fxScope.launch {
            val schedules = deferredSchedules.await()
            for ((schedule, credits, grade) in schedules) {
                // It doesn't give more precise times than in minutes
                val minutes = schedule.sumOf { meetings -> meetings.meetingTimes.sumOf { (it.endTime - it.startTime).inWholeMinutes } }
                val hours = minutes / 60
                val calendar = Calendar("Classes: ${schedule.size} Time: $hours:${minutes - (hours * 60)} Credits: $credits Grade: $grade", null)

                for (classData in schedule) {
                    for (time in classData.meetingTimes) {
                        val now = LocalDate.now().withWeekday((time.meetDay + 1).mod(7))
                        val startHours = time.startTime.inWholeHours.toInt()
                        val endHours = time.endTime.inWholeHours.toInt()
                        val interval = Interval(
                            now.atTime(startHours, time.startTime.inWholeMinutes.toInt() - (startHours * 60)),
                            now.atTime(endHours, time.endTime.inWholeMinutes.toInt() - (endHours * 60))
                        )

                        calendar.addEntry(Entry<Nothing?>(classData.title, interval))
                    }
                }

                source.calendars.add(calendar)
            }
        }

        fxScope.launch {
            while (true) {
                calendarView.time = LocalTime.now()
                calendarView.today = LocalDate.now()
                delay(1000)
            }
        }

        calendarView.calendarSources.add(source)

        val scene = Scene(calendarView)

        stage.title = "Calendar"
        stage.setScene(scene)
        stage.isMaximized = true
        stage.centerOnScreen()

        stage.setOnCloseRequest {
            Platform.exit()
            exitProcess(0)
        }

        stage.show()
    }
}

fun main() {
    launch(App::class.java)
}
