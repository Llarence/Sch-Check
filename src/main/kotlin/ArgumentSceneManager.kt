import javafx.event.EventHandler
import javafx.scene.control.*
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import org.controlsfx.control.textfield.CustomTextField
import java.io.File

private val argumentSaver = Saver.create<ScheduleGenArgument>(File("saves/arguments/"))

object ArgumentSceneManager {
    val root = VBox()

    private val classGroupsExpandable = Expandable(VBox()) {
        val children = listOf(
            EmptiableTextField().apply { right = Label("Weight") },
            Expandable(HBox()) {
                EmptiableTextField().apply { right = Label("Class") }
            }
        )

        EmptiableHBox(children)
    }

    private val termSelector = ComboBox<Term>()

    private val breaksPicker = Expandable(HBox()) {
        BreakAndWeightPicker()
    }

    private val creditWeightField = CustomTextField()
    private val backToBackWeightField = CustomTextField()

    private val creditLimitField = CustomTextField()
    private val creditLimitWeightField = CustomTextField()

    val doneButton = Button("Done")

    init {
        val header = HBox()

        val nameField = CustomTextField()
        nameField.right = Label("Name")

        val saveButton = Button("Save")
        saveButton.onAction = EventHandler {
            val alert = Alert(Alert.AlertType.CONFIRMATION, "Save?", ButtonType.YES, ButtonType.NO)
            alert.showAndWait()

            if (alert.result == ButtonType.YES) {
                argumentSaver.save(nameField.text, getArgument())
            }
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

        creditWeightField.right = Label("Credit Weight")

        backToBackWeightField.right = Label("Back To Back Weight")

        creditLimitField.right = Label("Credit Limit")
        creditLimitWeightField.right = Label("Credit Limit Weight")

        root.children.addAll(
            header,
            classGroupsExpandable,
            termSelector,
            breaksPicker,
            creditWeightField,
            backToBackWeightField,
            creditLimitField,
            creditLimitWeightField,
            doneButton)
    }

    private fun setArgument(argument: ScheduleGenArgument) {
        classGroupsExpandable.set(argument.classGroups.size) { i ->
            val children = listOf(
                EmptiableTextField().apply {
                    text = argument.gradeFunGeneratorArguments.groupWeights[i].toString()
                    right = Label("Weight")
                },
                Expandable(HBox()) {
                    EmptiableTextField().apply { right = Label("Class") }
                }.apply {
                    set(argument.classGroups[i].size) {
                        EmptiableTextField().apply {
                            text = argument.classGroups[i][it]
                            right = Label("Class")
                        }
                    }
                }
            )

            EmptiableHBox(children)
        }

        termSelector.value = argument.term

        breaksPicker.set(argument.gradeFunGeneratorArguments.breaksAndWeights.size) {
            val (currBreak, weight) = argument.gradeFunGeneratorArguments.breaksAndWeights[it]
            BreakAndWeightPicker(currBreak, weight)
        }

        creditWeightField.text = argument.gradeFunGeneratorArguments.creditWeight.toString()

        backToBackWeightField.text = argument.gradeFunGeneratorArguments.backToBackWeight.toString()

        creditLimitField.text = argument.gradeFunGeneratorArguments.creditLimit.toString()
        creditLimitWeightField.text = argument.gradeFunGeneratorArguments.creditLimitWeight.toString()
    }

    fun getArgument(): ScheduleGenArgument {
        val classGroups = classGroupsExpandable.getSubNodes().dropLast(1).map { subNode ->
            @Suppress("UNCHECKED_CAST")
            (subNode.children[1] as Expandable<EmptiableTextField>).getSubNodes().map { it.text }
        }

        val groupWeights = classGroupsExpandable.getSubNodes().dropLast(1)
            .map { subNode -> (subNode.children[0] as EmptiableTextField).text.toDoubleOrNull() ?: 0.0 }
        val breaksAndWeights = breaksPicker.getSubNodes().map { it.get() }

        val genArgument = GradeFunGenArgument(groupWeights,
            breaksAndWeights,
            creditWeightField.text.toDoubleOrNull() ?: 0.0,
            backToBackWeightField.text.toDoubleOrNull() ?: 0.0,
            creditLimitField.text.toIntOrNull() ?: 24,
            creditLimitWeightField.text.toDoubleOrNull() ?: 0.0)

        return ScheduleGenArgument(classGroups, termSelector.value, genArgument)
    }
}
