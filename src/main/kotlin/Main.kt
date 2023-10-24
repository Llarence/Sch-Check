import com.calendarfx.model.Calendar
import com.calendarfx.model.CalendarSource
import com.calendarfx.model.Entry
import com.calendarfx.model.Interval
import com.calendarfx.view.CalendarView
import com.sun.javafx.util.Utils.clamp
import javafx.application.Application
import javafx.application.Application.launch
import javafx.application.Platform
import javafx.event.Event
import javafx.event.EventHandler
import javafx.event.EventType
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.TextField
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.scene.text.Text
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

@OptIn(ExperimentalCoroutinesApi::class)
class App : Application() {
    private lateinit var stage: Stage

    private val loadScene: Scene

    private val loadText = Text()

    private val calendarScene: Scene

    private val calendar: Calendar<Nothing>
    val nameField = TextField()

    var calendarNameFormat = ""

    lateinit var deferredSchedules: Deferred<List<Schedule>>
    var scheduleIndex = 0

    init {
        val loadVBox = VBox()

        loadVBox.children.add(loadText)

        loadScene = Scene(loadVBox)
    }

    init {
        val calendarVBox = VBox()

        val headerHBox = HBox()

        val prevButton = Button("Previous")
        prevButton.onAction = EventHandler { shiftSchedule(-1) }

        val nextButton = Button("Next")
        nextButton.onAction = EventHandler { shiftSchedule(1) }

        nameField.textProperty().addListener { _, _, new ->
            setCalendarName(new)
        }

        headerHBox.children.addAll(prevButton, nextButton, nameField)

        val calendarView = CalendarView()
        calendarVBox.heightProperty().addListener { _, _, new ->
            calendarView.prefHeight = new.toDouble()
        }

        val source = CalendarSource("Schedule")

        calendar = Calendar<Nothing>("")
        source.calendars.add(calendar)

        calendarView.calendarSources.add(source)

        calendarVBox.children.addAll(headerHBox, calendarView)

        calendarScene = Scene(calendarVBox)

        fxScope.launch {
            while (true) {
                calendarView.time = LocalTime.now()
                calendarView.today = LocalDate.now()
                delay(1000)
            }
        }
    }

    private fun setSceneCalendar() {
        fxScope.launch {
            deferredSchedules = coroutineScope.async {
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

            loadText.text = "Loading"
            val showLoad = launch {
                while (true) {
                    loadText.text = "Loading"
                    delay(300)

                    loadText.text = "Loading."
                    delay(300)

                    loadText.text = "Loading.."
                    delay(300)

                    loadText.text = "Loading..."
                    delay(300)
                }
            }

            stage.scene = loadScene

            deferredSchedules.await()

            showLoad.cancelAndJoin()

            scheduleIndex = 0
            val first = deferredSchedules.getCompleted().firstOrNull()
            if (first != null) {
                setCalendar(scheduleIndex.toString(), first)
            }

            stage.scene = calendarScene
        }
    }

    private fun setCalendarName(name: String) {
        calendar.name = calendarNameFormat.format(name)
        nameField.text = name
    }

    private fun shiftSchedule(change: Int) {
        val schedules = deferredSchedules.getCompleted()

        if (schedules.isEmpty()) {
            return
        }

        scheduleIndex = (scheduleIndex + change).mod(schedules.size)
        setCalendar(scheduleIndex.toString(), schedules[scheduleIndex])
    }

    private fun setCalendar(name: String, schedule: Schedule) {
        calendarNameFormat = "Name: %s\nCredits: ${schedule.credits}\nGrade: ${schedule.grade}"
        setCalendarName(name)

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
        this.stage = stage

        stage.title = "Calendar"
        stage.isMaximized = true
        stage.centerOnScreen()

        stage.setOnCloseRequest {
            Platform.exit()
            exitProcess(0)
        }

        setSceneCalendar()

        stage.show()
    }
}

fun main() {
    launch(App::class.java)
}
