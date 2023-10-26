import javafx.beans.property.BooleanProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.scene.Node
import javafx.scene.control.CheckBox
import javafx.scene.control.TextField
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import javafx.scene.layout.VBox
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds

fun dayNumToChar(day: Int): String {
    return when (day) {
        0 -> "S"
        1 -> "M"
        2 -> "T"
        3 -> "W"
        4 -> "R"
        5 -> "F"
        6 -> "U"
        else -> throw IllegalArgumentException()
    }
}

fun Duration.toMeetString(): String {
    val hours = this.inWholeHours
    return "$hours:${this.inWholeMinutes - (hours * 60)}"
}

fun String.toMeetDurationOrNull(): Duration? {
    val split = this.split(':')
    if (split.size == 2) {
        val hours = split[0].toDoubleOrNull()
        val minutes = split[1].toDoubleOrNull()

        if (hours != null && minutes != null) {
            return hours.hours + minutes.minutes
        }
    }

    return null
}

interface Emptiable {
    val empty: BooleanProperty
}

class EmptiableTextField(text: String = "") : TextField(text), Emptiable {
    override val empty = SimpleBooleanProperty(text == "")

    init {
        textProperty().addListener { _, _, new ->
            empty.set(new == "")
        }
    }
}

class BreakAndWeightPicker(currBreak: Break? = null, currWeight: Double? = null) : VBox(), Emptiable {
    override val empty = SimpleBooleanProperty(true)

    private val dayCheckBoxes = List(7) { CheckBox(dayNumToChar(it)) }

    private val startTime = TextField()
    private val endTime = TextField()

    private val weight = TextField()

    init {
        val checkBoxesHBox = HBox()

        checkBoxesHBox.children.addAll(dayCheckBoxes)

        val emptyListener = { _: Any?, _: Any?, _: Any? ->
            empty.set(startTime.text == "" && endTime.text == "" && weight.text == "")
        }

        startTime.textProperty().addListener(emptyListener)
        endTime.textProperty().addListener(emptyListener)

        weight.textProperty().addListener(emptyListener)

        if (currBreak != null) {
            for (day in currBreak.meetDays) {
                dayCheckBoxes[day].isSelected = true
            }

            startTime.text = currBreak.startTime.toMeetString()
            endTime.text = currBreak.endTime.toMeetString()
        }

        if (currWeight != null) {
            weight.text = currWeight.toString()
        }

        children.addAll(checkBoxesHBox, startTime, endTime, weight)
    }

    fun get(): Pair<Break, Double> {
        val startTimeDuration = startTime.text.toMeetDurationOrNull()
        val endTimeDuration = endTime.text.toMeetDurationOrNull()
        val weightDouble = weight.text.toDoubleOrNull()

        val days = mutableListOf<Int>()
        if (startTimeDuration != null && endTimeDuration != null && weightDouble != null) {
            for (i in dayCheckBoxes.indices) {
                if (dayCheckBoxes[i].isSelected) {
                    days.add(i)
                }
            }

            return Pair(Break(days, startTimeDuration, endTimeDuration), weightDouble)
        }

        return Pair(Break(listOf(), 0.nanoseconds, 0.nanoseconds), 0.0)
    }
}

class Expandable<T>(private val container: Pane, private val genFun: () -> T) : Pane(), Emptiable where T : Node, T : Emptiable {
    override val empty = SimpleBooleanProperty(true)

    // Mostly to avoid unchecked casts
    private val subNodes = mutableListOf<T>()

    init {
        children.add(container)

        add()
    }

    private fun add(node: T = genFun()) {
        node.empty.addListener { _, _, isEmpty ->
            if (isEmpty) {
                val last = container.children.last()
                if (last != node) {
                    container.children.remove(node)
                }

                empty.set(container.children.size == 1)
            } else {
                empty.set(false)
                add()
            }
        }

        container.children.add(node)
        subNodes.add(node)
    }

    fun getSubNodes(): List<T> {
        return if (subNodes.last().empty.get()) {
            subNodes.dropLast(1)
        } else {
            subNodes
        }
    }

    fun set(items: Int, currGenFun: (Int) -> T) {
        container.children.clear()
        subNodes.clear()

        for (i in 0..<items) {
            val node = currGenFun(i)
            if (!node.empty.get()) {
                add(node)
            }
        }

        add()

        empty.set(container.children.size == 1)
    }
}
