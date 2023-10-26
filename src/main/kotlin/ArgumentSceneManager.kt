import javafx.event.EventHandler
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.ComboBox
import javafx.scene.control.TextField
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import java.io.File

val argumentSaver = Saver.create<ScheduleGenArgument>(File("saves/arguments/"))

object ArgumentSceneManager {
    val scene: Scene

    private val classGroupsExpandable = Expandable(VBox()) {
        Expandable(HBox()) {
            EmptiableTextField()
        }
    }

    private val termSelector = ComboBox<Term>()

    private val breaksPicker = Expandable(HBox()) {
        BreakAndWeightPicker()
    }

    private val creditWeightField = TextField()

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

        argumentVBox.children.addAll(header, classGroupsExpandable, termSelector, breaksPicker, creditWeightField, doneButton)

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

        breaksPicker.set(argument.gradeFunGeneratorArguments.breaksAndWeights.size) {
            val (currBreak, weight) = argument.gradeFunGeneratorArguments.breaksAndWeights[it]
            BreakAndWeightPicker(currBreak, weight)
        }

        creditWeightField.text = argument.gradeFunGeneratorArguments.creditWeight.toString()
    }

    fun getArgument(): ScheduleGenArgument {
        val classGroups = classGroupsExpandable.getSubNodes()
            .map { subExpandable -> subExpandable.getSubNodes().map { it.text } }

        val breaksAndWeights = breaksPicker.getSubNodes().map { it.get() }
        val creditWeight = creditWeightField.text.toDoubleOrNull() ?: 0.0
        val genArgument = GradeFunGenArgument(breaksAndWeights, creditWeight)

        return ScheduleGenArgument(classGroups, termSelector.value, genArgument)
    }
}
