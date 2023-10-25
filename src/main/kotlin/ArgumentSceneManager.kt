import javafx.beans.property.BooleanProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.scene.Node
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.TextField
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import javafx.scene.layout.VBox

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

    private fun add() {
        val node = genFun()

        node.empty.addListener { _, _, isEmpty ->
            if (isEmpty) {
                val last = container.children.last()
                if (last != node) {
                    container.children.remove(node)
                    empty.set(container.children.size == 1 && (last as T).empty.get())
                } else {
                    empty.set(container.children.size == 1)
                }
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
}

object ArgumentSceneManager {
    val scene: Scene

    private val classGroupsExpandable = Expandable(VBox()) {
        Expandable(HBox()) {
            EmptiableTextField()
        }
    }

    val doneButton = Button("Done")

    init {
        val argumentVBox = VBox()

        argumentVBox.children.addAll(classGroupsExpandable, doneButton)

        scene = Scene(argumentVBox)
    }

    fun getArgument(): ScheduleGenArgument {
        val classGroups = classGroupsExpandable.getSubNodes()
            .map { subExpandable -> subExpandable.getSubNodes().map { it.text } }

        return ScheduleGenArgument(classGroups, GradeFunGenArgument(listOf(), 0.0, 1.0))
    }
}