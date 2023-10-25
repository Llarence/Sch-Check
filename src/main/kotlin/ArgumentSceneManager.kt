import javafx.beans.property.BooleanProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.event.EventHandler
import javafx.scene.Node
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.ComboBox
import javafx.scene.control.TextField
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import javafx.scene.layout.VBox
import java.io.File

val argumentSaver = Saver.create<ScheduleGenArgument>(File("saves/arguments/"))

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

class Expandable<T>(private val container: Pane, private val genFun: () -> T) : Pane(), Emptiable where T : Node, T : Emptiable {
    override val empty = SimpleBooleanProperty(true)

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
    }

    fun getSubNodes(): List<T> {
        val subNodes = container.children.map { it as T }

        return if (subNodes.last().empty.get()) {
            subNodes.dropLast(1)
        } else {
            subNodes
        }
    }

    fun set(items: Int, currGenFun: (Int) -> T) {
        container.children.clear()
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

object ArgumentSceneManager {
    val scene: Scene

    private val classGroupsExpandable = Expandable(VBox()) {
        Expandable(HBox()) {
            EmptiableTextField()
        }
    }

    private val termSelector = ComboBox<Term>()

    val doneButton = Button("Done")

    init {
        val argumentVBox = VBox()

        val header = HBox()

        val nameField = TextField()

        val saveButton = Button("Save")
        saveButton.onAction = EventHandler {
            argumentSaver.save(nameField.text, getArgument())
        }

        val loadButton = Button("Load")
        loadButton.onAction = EventHandler {
            val argument = argumentSaver.load(nameField.text)
            if (argument != null) {
                setArgument(argument)
            }
        }

        header.children.addAll(nameField, saveButton, loadButton)

        termSelector.items.addAll(Term.FALL, Term.WINTER, Term.SPRING, Term.SUMMER)
        termSelector.value = Term.FALL

        argumentVBox.children.addAll(header, classGroupsExpandable, termSelector, doneButton)

        scene = Scene(argumentVBox)
    }

    private fun setArgument(argument: ScheduleGenArgument) {
        classGroupsExpandable.set(argument.classGroups.size) { i ->
            val expandable = Expandable(HBox()) {
                EmptiableTextField()
            }

            expandable.set(argument.classGroups[i].size) {
                EmptiableTextField(argument.classGroups[i][it])
            }

            expandable
        }

        termSelector.value = argument.term
    }

    fun getArgument(): ScheduleGenArgument {
        val classGroups = classGroupsExpandable.getSubNodes()
            .map { subExpandable -> subExpandable.getSubNodes().map { it.text } }

        return ScheduleGenArgument(classGroups, termSelector.value, GradeFunGenArgument(listOf(), 0.0, 1.0))
    }
}