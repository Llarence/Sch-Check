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
import javafx.scene.control.Button
import javafx.scene.control.TextField
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.scene.text.Text
import javafx.stage.Stage
import kotlinx.coroutines.*
import kotlinx.coroutines.javafx.JavaFx
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.atomic.AtomicReference
import kotlin.system.exitProcess

val coroutineScope = CoroutineScope(Dispatchers.Default)
val fxScope = CoroutineScope(Dispatchers.JavaFx)

val calendarSaver = Saver(File("saves/calendars/"))

fun LocalDate.withScheduleWeekday(day: Int): LocalDate {
    return this.plusDays((day + 1).mod(7).toLong() - this.dayOfWeek.value)
}

// TODO: Add calendar name checking for the filename or maybe convert
//  the filename to something valid with regex or something
@OptIn(ExperimentalCoroutinesApi::class)
class App : Application() {
    private lateinit var stage: Stage

    private val loadScene: Scene

    private val loadText = Text()

    private val calendarScene: Scene

    private val calendar: Calendar<Nothing>
    private val nameField = TextField()

    private var calendarNameFormat = ""

    private lateinit var schedules: List<Schedule>
    private var scheduleIndex = 0

    private var currSchedule: Schedule? = null

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

        val saveButton = Button("Save")
        saveButton.onAction = EventHandler {
            if (currSchedule != null) {
                calendarSaver.save(nameField.text, currSchedule!!)
            }
        }

        val loadButton = Button("Load")
        loadButton.onAction = EventHandler {
            val schedule = calendarSaver.load(nameField.text)
            if (schedule != null) {
                setCalendar(nameField.text, schedule)
            }
        }

        headerHBox.children.addAll(prevButton, nextButton, nameField, saveButton, loadButton)

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
            val text = AtomicReference<String>()
            loadText.text = text.get()

            val textUpdater = fxScope.launch {
                while (true) {
                    delay(100)
                    loadText.text = text.get()
                }
            }

            stage.scene = loadScene

            schedules = coroutineScope.async {
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
                    ), Term.WINTER,
                    400000,
                    genScheduleGrader(listOf(), 0.0, 1.0)) { description, percent ->
                    text.set(String.format("%s: %.2f%%", description, percent * 100))
                }
            }.await()

            textUpdater.cancelAndJoin()

            scheduleIndex = 0
            val first = schedules.firstOrNull()
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
        if (schedules.isEmpty()) {
            return
        }

        scheduleIndex = (scheduleIndex + change).mod(schedules.size)
        setCalendar(scheduleIndex.toString(), schedules[scheduleIndex])
    }

    private fun setCalendar(name: String, schedule: Schedule) {
        currSchedule = schedule

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
