import com.calendarfx.model.Calendar
import com.calendarfx.model.CalendarSource
import com.calendarfx.model.Entry
import com.calendarfx.model.Interval
import com.calendarfx.view.CalendarView
import javafx.application.Application
import javafx.application.Application.launch
import javafx.application.Platform
import javafx.scene.Scene
import javafx.stage.Stage
import kotlinx.coroutines.*
import kotlinx.coroutines.javafx.JavaFx
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import kotlin.system.exitProcess

val coroutineScope = CoroutineScope(Dispatchers.Default)
val fxScope = CoroutineScope(Dispatchers.JavaFx)

fun LocalDate.withScheduleWeekday(day: Int): LocalDate {
    return this.plusDays((day + 1).mod(7).toLong() - this.dayOfWeek.value)
}

class App : Application() {
    private val calendar: Calendar<Nothing>
    private val calendarScene: Scene

    init {
        val calendarView = CalendarView()
        val source = CalendarSource("Schedule")
        calendar = Calendar<Nothing>("")

        source.calendars.add(calendar)
        calendarView.calendarSources.add(source)

        calendarScene = Scene(calendarView)

        fxScope.launch {
            while (true) {
                calendarView.time = LocalTime.now()
                calendarView.today = LocalDate.now()
                delay(1000)
            }
        }
    }

    private fun setCalendar(name: String, schedule: Schedule) {
        calendar.name = "Name: $name\nCredits: ${schedule.credits}\nGrade: ${schedule.grade}"

        calendar.clear()
        val now = LocalDate.now()
        for (classDatum in schedule.classData) {
            for (meetTime in classDatum.meetingTimes) {
                val date = now.withScheduleWeekday(meetTime.meetDay)

                val startHours = meetTime.startTime.inWholeHours.toInt()
                val endHours = meetTime.endTime.inWholeHours.toInt()

                val interval = Interval(
                    date.atTime(startHours, meetTime.startTime.inWholeMinutes.toInt() - (startHours * 60)),
                    date.atTime(endHours, meetTime.endTime.inWholeMinutes.toInt() - (endHours * 60))
                )

                calendar.addEntry(Entry<Nothing>(classDatum.title, interval))
            }
        }
    }

    override fun start(stage: Stage) {
        stage.title = "Calendar"
        stage.setScene(calendarScene)
        stage.isMaximized = true
        stage.centerOnScreen()

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

        fxScope.launch {
            setCalendar("1", deferredSchedules.await().first())
        }

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
