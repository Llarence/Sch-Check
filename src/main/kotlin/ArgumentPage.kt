import javafx.event.EventHandler
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.util.StringConverter
import kotlinx.serialization.Serializable
import java.io.File
import java.time.DayOfWeek

@Serializable
data class ScheduleGenArguments(val classGroupSearches: List<List<Search>>, val tries: Int, val skipChance: Double)

private val argumentSaver = Saver.create<ScheduleGenArguments>(File("saves/arguments/"))
private val optionStringConverter = object : StringConverter<Option>() {
    override fun toString(option: Option?): String {
        return option?.description ?: ""
    }

    override fun fromString(string: String): Option {
        throw NotImplementedError()
    }
}

class DayTimePicker : VBox() {
    private val hour = Spinner<Int>(0, 23, 0)
    private val minute = Spinner<Int>(0, 59, 0)

    init {
        children.addAll(Label("Hour", hour), Label("Minute", minute))
    }

    fun getValue(): DayTime {
        return DayTime(hour.value, minute.value)
    }
}

class SearchPane(val term: Option) : VBox() {
    private val subject = ComboBox<Option>()
    private val courseNumber = Spinner<Int>(0, Int.MAX_VALUE, Int.MIN_VALUE)
    private val keyword = TextField()
    private val keywordAll = TextField()
    private val keywordAny = TextField()
    private val keywordExact = TextField()
    private val keywordWithout = TextField()
    private val attribute = ComboBox<Option>()
    private val campus = ComboBox<Option>()
    private val level = ComboBox<Option>()
    private val building = ComboBox<Option>()
    private val college = ComboBox<Option>()
    private val department = ComboBox<Option>()
    private val scheduleType = ComboBox<Option>()
    private val durationValue = Spinner<Double>()
    private val durationType = ComboBox<Option>()
    private val partOfTerm = ComboBox<Option>()
    private val courseNumberRangeLow = Spinner<Int>(0, Int.MAX_VALUE, Int.MIN_VALUE)
    private val courseNumberRangeHigh = Spinner<Int>(0, Int.MAX_VALUE, Int.MIN_VALUE)
    private val monday = CheckBox("Monday")
    private val tuesday = CheckBox("Tuesday")
    private val wednesday = CheckBox("Wednesday")
    private val thursday = CheckBox("Thursday")
    private val friday = CheckBox("Friday")
    private val saturday = CheckBox("Saturday")
    private val sunday = CheckBox("Sunday")
    private val start = DayTimePicker()
    private val end = DayTimePicker()
    private val openOnly = CheckBox("Open Only")

    init {
        val searchOptions = searchOptions[term]!!

        children.addAll(
            addEnable(labelComboBox(subject, "Subject", searchOptions.subjects)),
            addEnable(Label("Course Number", courseNumber)),
            addEnable(Label("Keyword", keyword)),
            addEnable(Label("Keyword All", keywordAll)),
            addEnable(Label("Keyword Any", keywordAny)),
            addEnable(Label("Keyword Exact", keywordExact)),
            addEnable(Label("Keyword Without", keywordWithout)),
            addEnable(labelComboBox(attribute, "Attribute", searchOptions.attributes)),
            addEnable(labelComboBox(campus, "Campus", searchOptions.campuses)),
            addEnable(labelComboBox(level, "Level", searchOptions.levels)),
            addEnable(labelComboBox(building, "Building", searchOptions.buildings)),
            addEnable(labelComboBox(college, "College", searchOptions.colleges)),
            addEnable(labelComboBox(department, "Department", searchOptions.departments)),
            addEnable(labelComboBox(scheduleType, "Schedule Type", searchOptions.scheduleTypes)),
            addEnable(Label("Duration Value", durationValue)),
            addEnable(labelComboBox(durationType, "Duration Type", searchOptions.durationTypes)),
            addEnable(labelComboBox(partOfTerm, "Part Of Term", searchOptions.partsOfTerm)),
            addEnable(Label("Course Number Low", courseNumberRangeLow)),
            addEnable(Label("Course Number High", courseNumberRangeHigh)),
            monday,
            tuesday,
            wednesday,
            thursday,
            friday,
            saturday,
            sunday,
            addEnable(start),
            addEnable(end),
            openOnly
        )
    }

    private fun labelComboBox(comboBox: ComboBox<Option>, label: String, options: List<Option>): Label {
        comboBox.items.addAll(options)
        comboBox.selectionModel.select(0)
        comboBox.converter = optionStringConverter
        return Label(label, comboBox)
    }

    private fun addEnable(node: Node): HBox {
        node.isDisable = true

        val checkBox = CheckBox()
        checkBox.selectedProperty().addListener { _, _, value ->
            node.isDisable = !value
        }

        return HBox(checkBox, node)
    }

    fun getSearch(): Search {
        val days = mutableSetOf<DayOfWeek>()
        if (monday.isSelected) { days.add(DayOfWeek.MONDAY) }
        if (tuesday.isSelected) { days.add(DayOfWeek.TUESDAY) }
        if (wednesday.isSelected) { days.add(DayOfWeek.WEDNESDAY) }
        if (thursday.isSelected) { days.add(DayOfWeek.THURSDAY) }
        if (friday.isSelected) { days.add(DayOfWeek.FRIDAY) }
        if (saturday.isSelected) { days.add(DayOfWeek.SATURDAY) }
        if (sunday.isSelected) { days.add(DayOfWeek.SUNDAY) }

        return Search(
            subject = if (subject.isDisable) { null } else { subject.value.code },
            courseNumber = if (courseNumber.isDisable) { null } else { courseNumber.value },
            keyword = if (keyword.isDisable) { null } else { keyword.text },
            keywordAll = if (keywordAll.isDisable) { null } else { keywordAll.text },
            keywordAny = if (keywordAny.isDisable) { null } else { keywordAny.text },
            keywordExact = if (keywordExact.isDisable) { null } else { keywordExact.text },
            keywordWithout = if (keywordWithout.isDisable) { null } else { keywordWithout.text },
            attribute = if (attribute.isDisable) { null } else { attribute.value.code },
            campus = if (campus.isDisable) { null } else { campus.value.code },
            level = if (level.isDisable) { null } else { level.value.code },
            building = if (building.isDisable) { null } else { building.value.code },
            college = if (college.isDisable) { null } else { college.value.code },
            department = if (department.isDisable) { null } else { department.value.code },
            scheduleType = if (scheduleType.isDisable) { null } else { scheduleType.value.code },
            durationValue = if (durationValue.isDisable) { null } else { durationValue.value },
            durationType = if (durationType.isDisable) { null } else { durationType.value.code },
            partOfTerm = if (partOfTerm.isDisable) { null } else { partOfTerm.value.code },
            courseNumberRangeLow = if (courseNumberRangeLow.isDisable) { null } else { courseNumberRangeLow.value },
            courseNumberRangeHigh = if (courseNumberRangeHigh.isDisable) { null } else { courseNumberRangeHigh.value },
            days = days,
            start = if (start.isDisable) { null } else { start.getValue() },
            end = if (end.isDisable) { null } else { end.getValue() },
            openOnly = openOnly.isSelected,
            term = term.code
        )
    }
}

fun addAddTab(tabPane: TabPane, tabGen: () -> Tab) {
    val addButton = Tab("Add")
    addButton.isClosable = false
    tabPane.selectionModel.selectedItemProperty().addListener { _, _, newTab ->
        if(newTab == addButton) {
            val newTabIndex = tabPane.tabs.size - 1
            tabPane.tabs.add(newTabIndex, tabGen())
            tabPane.selectionModel.select(newTabIndex)
        }
    }

    tabPane.tabs.add(addButton)
}

class ArgumentPage(private val term: Option) : Page() {
    init {
        val tab = genClassGroupTab()
        tab.isClosable = false

        val tabPane = TabPane()
        tabPane.tabs.add(tab)
        addAddTab(tabPane, ::genClassGroupTab)

        mainVBox.children.addAll(tabPane)
    }

    private fun genClassGroupTab(): Tab {
        val subTab = genSearchTab()
        subTab.isClosable = false

        val subPane = TabPane()
        subPane.tabs.add(subTab)
        addAddTab(subPane, ::genSearchTab)

        val tab = Tab("Class Group")
        tab.content = subPane

        return tab
    }

    private fun genSearchTab(): Tab {
        val tab = Tab("Search")
        tab.content = SearchPane(term)

        return tab
    }
}

class TermSelectPage : Page() {
    private val termSelect = ComboBox<Option>()

    init {
        val next = Button("Next!")
        next.isDisable = true
        next.onAction = EventHandler { onDone() }

        termSelect.selectionModel.select(0)
        termSelect.items.addAll(searchOptions.keys)
        termSelect.converter = optionStringConverter
        termSelect.selectionModel.selectedItemProperty().addListener { _ -> next.isDisable = false }

        mainVBox.children.addAll(termSelect, next)
    }

    fun getTerm(): Option {
        return termSelect.value
    }
}
