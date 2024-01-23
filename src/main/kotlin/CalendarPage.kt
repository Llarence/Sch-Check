import com.calendarfx.model.Calendar
import com.calendarfx.model.CalendarSource
import com.calendarfx.model.Entry
import com.calendarfx.model.Interval
import com.calendarfx.view.CalendarView
import javafx.event.EventHandler
import javafx.scene.control.Alert
import javafx.scene.control.Alert.AlertType
import javafx.scene.control.Button
import javafx.scene.control.ButtonType
import javafx.scene.control.Label
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.controlsfx.control.textfield.CustomTextField
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

private val calendarSaver = Saver.create<List<ClassData>>(File("saves/calendars/"))

fun LocalDate.withScheduleWeekday(day: DayOfWeek): LocalDate {
    return this.plusDays((day.value - this.dayOfWeek.value).toLong())
}

class CalendarPage : Page() {
    private val nameField = CustomTextField()

    override val root = VBox()

    private val calendar: Calendar<Nothing>

    private var calendarNameFormat = ""

    private lateinit var schedules: List<List<ClassData>>
    private var scheduleIndex = 0

    private var currSchedule: List<ClassData>? = null

    init {
        val prevButton = Button("Previous")
        prevButton.onAction = EventHandler { shiftSchedule(-1) }

        val nextButton = Button("Next")
        nextButton.onAction = EventHandler { shiftSchedule(1) }

        nameField.right = Label("Name")
        nameField.textProperty().addListener { _, _, new ->
            setCalendarName(new)
        }

        // Hate to copy code from ArgumentPage
        val saveButton = Button("Save")
        saveButton.onAction = EventHandler {
            if (currSchedule != null) {
                val alert = Alert(AlertType.CONFIRMATION, "", ButtonType.YES, ButtonType.NO)
                alert.showAndWait()

                if (alert.result == ButtonType.YES) {
                    calendarSaver.save(nameField.text, currSchedule!!)
                }
            }
        }

        // Hate to copy code from ArgumentPage
        val loadButton = Button("Load")
        loadButton.onAction = EventHandler {
            val schedule = calendarSaver.load(nameField.text)
            if (schedule != null) {
                val alert = Alert(AlertType.CONFIRMATION, "", ButtonType.YES, ButtonType.NO)
                alert.showAndWait()

                if (alert.result == ButtonType.YES) {
                    setCalendar(nameField.text, schedule)
                }
            }
        }

        val headerHBox = HBox(prevButton, nextButton, nameField, saveButton, loadButton)

        calendar = Calendar<Nothing>("")

        val source = CalendarSource("Schedule")
        source.calendars.add(calendar)

        val calendarView = CalendarView()
        calendarView.calendarSources.add(source)

        root.heightProperty().addListener { _, _, new ->
            calendarView.prefHeight = new.toDouble()
        }
        root.children.addAll(headerHBox, calendarView)

        fxScope.launch {
            while (true) {
                calendarView.time = LocalTime.now()
                calendarView.today = LocalDate.now()
                delay(1000)
            }
        }
    }

    fun loadSchedules(schedules: List<List<ClassData>>) {
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

    private fun setCalendar(name: String, schedule: List<ClassData>) {
        currSchedule = schedule

        calendarNameFormat = "Name: %s"
        setCalendarName(name)

        calendar.clear()
        val now = LocalDate.now()
        for (classDatum in schedule) {
            for (meetTime in classDatum.meetTimes) {
                val date = now.withScheduleWeekday(meetTime.day)

                val interval = Interval(
                    date.atTime(meetTime.start.hour, meetTime.start.minute),
                    date.atTime(meetTime.end.hour, meetTime.end.minute)
                )

                // TODO: Make the entry un-modifiable
                val entry = Entry<Nothing>("CRN: ${classDatum.crn}", interval)
                entry.recurrenceRule = "RRULE:FREQ=WEEKLY"
                entry.styleClass.clear()
                entry.styleClass.add("entry")

                calendar.addEntry(entry)
            }
        }
    }
}