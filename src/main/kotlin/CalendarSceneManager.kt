import com.calendarfx.model.Calendar
import com.calendarfx.model.CalendarSource
import com.calendarfx.model.Entry
import com.calendarfx.model.Interval
import com.calendarfx.view.CalendarView
import javafx.event.EventHandler
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.controlsfx.control.textfield.CustomTextField
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

val calendarSaver = Saver.create<Schedule>(File("saves/calendars/"))

object CalendarSceneManager {
    val scene: Scene

    val backButton = Button("Back")

    private val calendar: Calendar<Nothing>
    private val nameField = CustomTextField()

    private var calendarNameFormat = ""

    private lateinit var schedules: List<Schedule>
    private var scheduleIndex = 0

    private var currSchedule: Schedule? = null

    init {
        val calendarVBox = VBox()

        val headerHBox = HBox()

        val prevButton = Button("Previous")
        prevButton.onAction = EventHandler { shiftSchedule(-1) }

        val nextButton = Button("Next")
        nextButton.onAction = EventHandler { shiftSchedule(1) }

        nameField.right = Label("Name")
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

        headerHBox.children.addAll(backButton, prevButton, nextButton, nameField, saveButton, loadButton)

        val calendarView = CalendarView()
        calendarVBox.heightProperty().addListener { _, _, new ->
            calendarView.prefHeight = new.toDouble()
        }

        val source = CalendarSource("Schedule")

        calendar = Calendar<Nothing>("")
        source.calendars.add(calendar)

        calendarView.calendarSources.add(source)

        calendarVBox.children.addAll(headerHBox, calendarView)

        scene = Scene(calendarVBox)

        fxScope.launch {
            while (true) {
                calendarView.time = LocalTime.now()
                calendarView.today = LocalDate.now()
                delay(1000)
            }
        }
    }

    fun loadSchedules(schedules: List<Schedule>) {
        this.schedules = schedules

        scheduleIndex = 0
        val first = schedules.firstOrNull()
        if (first != null) {
            setCalendar(scheduleIndex.toString(), first)
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
}