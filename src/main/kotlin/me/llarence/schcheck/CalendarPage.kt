package me.llarence.schcheck

import com.calendarfx.model.Calendar
import com.calendarfx.model.CalendarSource
import com.calendarfx.model.Entry
import com.calendarfx.model.Interval
import com.calendarfx.view.CalendarView
import javafx.event.EventHandler
import javafx.scene.control.*
import javafx.scene.control.Alert.AlertType
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
        val backButton = Button("Back")
        backButton.onAction = EventHandler { this.onBack() }

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

        val headerHBox = HBox(backButton, prevButton, nextButton, nameField, saveButton, loadButton)

        calendar = Calendar<Nothing>("")

        val source = CalendarSource("Schedule")
        source.calendars.add(calendar)

        val calendarView = CalendarView()
        calendarView.calendarSources.add(source)

        root.heightProperty().addListener { _, _, new ->
            calendarView.prefHeight = new.toDouble()
        }
        root.children.addAll(headerHBox, calendarView)

        fxGlobalScope.launch {
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
        setCalendar("0", schedules.firstOrNull())
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

    private fun setCalendar(name: String, schedule: List<ClassData>?) {
        currSchedule = schedule

        calendar.clear()

        if (schedule == null) {
            calendarNameFormat = ""
            setCalendarName(name)
            return
        }

        calendarNameFormat = "Name: %s, Credits: ${schedule.sumOf { it.credits ?: 0 }}"
        setCalendarName(name)

        val now = LocalDate.now()
        for (classDatum in schedule) {
            val meetTimes = classDatum.meetTimes.ifEmpty {
                listOf(null)
            }

            for (meetTime in meetTimes) {
                val date: LocalDate
                val interval: Interval
                if (meetTime == null) {
                    date = now.withScheduleWeekday(DayOfWeek.SATURDAY)
                    interval = Interval(date.atTime(11, 0), date.atTime(13, 0))
                } else {
                    date = now.withScheduleWeekday(meetTime.day)
                    interval = Interval(
                        date.atTime(meetTime.start.hour, meetTime.start.minute),
                        date.atTime(meetTime.end.hour, meetTime.end.minute)
                    )
                }

                // TODO: Make the entry un-modifiable
                val entry = Entry<Nothing>(
                    "CRN: ${classDatum.crn}, Title: ${classDatum.title}" +
                            if (meetTime == null) { ", NOT IN PERSON" } else { "" },
                    interval
                )

                entry.recurrenceRule = "RRULE:FREQ=WEEKLY"
                entry.styleClass.clear()
                entry.styleClass.add("entry")

                calendar.addEntry(entry)
            }
        }
    }
}

class LoadingPage : Page() {
    private val progressBar = ProgressBar()

    override val root = VBox()

    private var tasksDone = 0
    private var tasks = 0

    init {
        root.children.add(progressBar)

        clear()
    }

    fun clear() {
        tasksDone = 0
        tasks = 0

        progressBar.progress = 0.0
    }

    private fun update() {
       progressBar.progress = tasksDone.toDouble() / tasks
    }

    fun pushTask() {
        tasks++

        update()
    }

    fun popTask() {
        tasksDone++

        update()
    }
}
