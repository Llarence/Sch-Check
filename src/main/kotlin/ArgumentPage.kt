import javafx.event.EventHandler
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.util.StringConverter
import kotlinx.serialization.Serializable
import org.controlsfx.control.textfield.CustomTextField
import java.io.File
import java.time.DayOfWeek

@Serializable
data class ScheduleGenArguments(val classGroupsSearches: List<List<Search>>, val tries: Int, val skipChance: Double)

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

class SearchPane(private val term: Option, default: Search = Search(term = term.code)) : VBox() {
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

        if (default.courseNumber != null) { courseNumber.valueFactory.value = default.courseNumber }
        if (default.keyword != null) { keyword.text = default.keyword }
        if (default.keywordAll != null) { keywordAll.text = default.keywordAll }
        if (default.keywordAny != null) { keywordAny.text = default.keywordAny }
        if (default.keywordExact != null) { keywordExact.text = default.keywordExact }
        if (default.keywordWithout != null) { keywordWithout.text = default.keywordWithout }
        if (default.durationValue != null) { durationValue.valueFactory.value = default.durationValue }
        if (default.courseNumberRangeLow != null) { courseNumberRangeLow.valueFactory.value = default.courseNumberRangeLow }
        if (default.courseNumberRangeHigh != null) { courseNumberRangeHigh.valueFactory.value = default.courseNumberRangeHigh }
        monday.isSelected = DayOfWeek.MONDAY in default.days
        tuesday.isSelected = DayOfWeek.TUESDAY in default.days
        wednesday.isSelected = DayOfWeek.WEDNESDAY in default.days
        thursday.isSelected = DayOfWeek.THURSDAY in default.days
        friday.isSelected = DayOfWeek.FRIDAY in default.days
        saturday.isSelected = DayOfWeek.SATURDAY in default.days
        sunday.isSelected = DayOfWeek.SUNDAY in default.days
        openOnly.isSelected = default.openOnly

        children.addAll(
            addEnable(labelComboBox(subject, "Subject", searchOptions.subjects, default.subject), default.subject != null),
            addEnable(Label("Course Number", courseNumber), default.courseNumber != null),
            addEnable(Label("Keyword", keyword), default.keyword != null),
            addEnable(Label("Keyword All", keywordAll), default.keywordAll != null),
            addEnable(Label("Keyword Any", keywordAny), default.keywordAny != null),
            addEnable(Label("Keyword Exact", keywordExact), default.keywordExact != null),
            addEnable(Label("Keyword Without", keywordWithout), default.keywordWithout != null),
            addEnable(labelComboBox(attribute, "Attribute", searchOptions.attributes, default.attribute), default.attribute != null),
            addEnable(labelComboBox(campus, "Campus", searchOptions.campuses, default.campus), default.campus != null),
            addEnable(labelComboBox(level, "Level", searchOptions.levels, default.level), default.level != null),
            addEnable(labelComboBox(building, "Building", searchOptions.buildings, default.building), default.building != null),
            addEnable(labelComboBox(college, "College", searchOptions.colleges, default.college), default.college != null),
            addEnable(labelComboBox(department, "Department", searchOptions.departments, default.department), default.department != null),
            addEnable(labelComboBox(scheduleType, "Schedule Type", searchOptions.scheduleTypes, default.scheduleType), default.scheduleType != null),
            addEnable(Label("Duration Value", durationValue), default.courseNumberRangeLow != null),
            addEnable(labelComboBox(durationType, "Duration Type", searchOptions.durationTypes, default.durationType), default.durationType != null),
            addEnable(labelComboBox(partOfTerm, "Part Of Term", searchOptions.partsOfTerm, default.partOfTerm), default.partOfTerm != null),
            addEnable(Label("Course Number Low", courseNumberRangeLow), default.courseNumberRangeLow != null),
            addEnable(Label("Course Number High", courseNumberRangeHigh), default.courseNumberRangeHigh != null),
            monday,
            tuesday,
            wednesday,
            thursday,
            friday,
            saturday,
            sunday,
            addEnable(start, default.start != null),
            addEnable(end, default.end != null),
            openOnly
        )
    }

    private fun labelComboBox(comboBox: ComboBox<Option>, label: String, options: List<Option>, default: String?): Label {
        comboBox.items.addAll(options)

        if (default == null) {
            comboBox.selectionModel.select(0)
        } else {
            comboBox.selectionModel.select(options.first { it.code == default })
        }

        comboBox.converter = optionStringConverter
        return Label(label, comboBox)
    }

    private fun addEnable(node: Node, enabled: Boolean): HBox {
        val checkBox = CheckBox()
        if (enabled) {
            node.isDisable = false
            checkBox.isSelected = true
        } else {
            node.isDisable = true
        }

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
            subject = if (subject.isDisabled) { null } else { subject.value.code },
            courseNumber = if (courseNumber.isDisabled) { null } else { courseNumber.value },
            keyword = if (keyword.isDisabled) { null } else { keyword.text },
            keywordAll = if (keywordAll.isDisabled) { null } else { keywordAll.text },
            keywordAny = if (keywordAny.isDisabled) { null } else { keywordAny.text },
            keywordExact = if (keywordExact.isDisabled) { null } else { keywordExact.text },
            keywordWithout = if (keywordWithout.isDisabled) { null } else { keywordWithout.text },
            attribute = if (attribute.isDisabled) { null } else { attribute.value.code },
            campus = if (campus.isDisabled) { null } else { campus.value.code },
            level = if (level.isDisabled) { null } else { level.value.code },
            building = if (building.isDisabled) { null } else { building.value.code },
            college = if (college.isDisabled) { null } else { college.value.code },
            department = if (department.isDisabled) { null } else { department.value.code },
            scheduleType = if (scheduleType.isDisabled) { null } else { scheduleType.value.code },
            durationValue = if (durationValue.isDisabled) { null } else { durationValue.value },
            durationType = if (durationType.isDisabled) { null } else { durationType.value.code },
            partOfTerm = if (partOfTerm.isDisabled) { null } else { partOfTerm.value.code },
            courseNumberRangeLow = if (courseNumberRangeLow.isDisabled) { null } else { courseNumberRangeLow.value },
            courseNumberRangeHigh = if (courseNumberRangeHigh.isDisabled) { null } else { courseNumberRangeHigh.value },
            days = days,
            start = if (start.isDisabled) { null } else { start.getValue() },
            end = if (end.isDisabled) { null } else { end.getValue() },
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
    private val tabPane = TabPane()

    override val root = ScrollPane()

    init {
        val nameField = CustomTextField()
        nameField.right = Label("Name")

        // Hate to copy code from CalendarPage
        val saveButton = Button("Save")
        saveButton.onAction = EventHandler {
            val alert = Alert(Alert.AlertType.CONFIRMATION, "", ButtonType.YES, ButtonType.NO)
            alert.showAndWait()

            if (alert.result == ButtonType.YES) {
                argumentSaver.save(nameField.text, getArgument())
            }
        }

        // Hate to copy code from CalendarPage
        val loadButton = Button("Load")
        loadButton.onAction = EventHandler {
            val argument = argumentSaver.load(nameField.text)
            if (argument != null) {
                val alert = Alert(Alert.AlertType.CONFIRMATION, "", ButtonType.YES, ButtonType.NO)
                alert.showAndWait()

                if (alert.result == ButtonType.YES) {
                    setArgument(argument)
                }
            }
        }

        val nextButton = Button("Next")
        nextButton.onAction = EventHandler { onDone() }

        val headerHBox = HBox(nameField, saveButton, loadButton, nextButton)

        val tab = genClassGroupTab()

        tabPane.tabs.add(tab)
        addAddTab(tabPane, ::genClassGroupTab)

        val mainVBox = VBox(headerHBox, tabPane)
        root.content = mainVBox
    }

    // Hate all the casting
    fun getArgument(): ScheduleGenArguments {
        val classGroupsSearches = mutableListOf<List<Search>>()
        for (i in 0..<tabPane.tabs.size - 1) {
            val classGroupSearches = mutableListOf<Search>()

            val subTabPane = tabPane.tabs[i].content as TabPane
            for (j in 0..<subTabPane.tabs.size - 1) {
                classGroupSearches.add((subTabPane.tabs[j].content as SearchPane).getSearch())
            }

            classGroupsSearches.add(classGroupSearches)
        }

        return ScheduleGenArguments(classGroupsSearches, 1000, 0.2)
    }

    // Hate all the casting
    private fun setArgument(argument: ScheduleGenArguments) {
        val size = tabPane.tabs.size - 1

        for (classGroupSearches in argument.classGroupsSearches.reversed()) {
            tabPane.tabs.add(size, genClassGroupTab(classGroupSearches.reversed().map { genSearchTab(it) }))
        }

        // Stops the add tab from being selected and trigger a new tab being created
        for (i in 0..<size) {
            tabPane.tabs.remove(tabPane.tabs[0])
        }
    }

    private fun genClassGroupTab(tabs: List<Tab>? = null): Tab {
        val subPane = TabPane()
        if (tabs != null) {
            subPane.tabs.addAll(tabs)
        }

        addAddTab(subPane, ::genSearchTab)

        val tab = Tab("Class Group")
        tab.content = subPane

        return tab
    }

    private fun genSearchTab(search: Search = Search(term = term.code)): Tab {
        val tab = Tab("Search")
        tab.content = SearchPane(term, search)

        return tab
    }
}

class TermSelectPage : Page() {
    private val termSelect = ComboBox<Option>()

    override val root = VBox()

    init {
        val next = Button("Next!")
        next.isDisable = true
        next.onAction = EventHandler { onDone() }

        termSelect.selectionModel.select(0)
        termSelect.items.addAll(searchOptions.keys)
        termSelect.converter = optionStringConverter
        termSelect.selectionModel.selectedItemProperty().addListener { _ -> next.isDisable = false }

        root.children.addAll(termSelect, next)
    }

    fun getTerm(): Option {
        return termSelect.value
    }
}
