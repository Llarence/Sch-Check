import javafx.event.EventHandler
import javafx.scene.control.*
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import kotlinx.serialization.Serializable
import org.controlsfx.control.textfield.CustomTextField
import java.io.File

@Serializable
data class ScheduleRankingArguments(val creditValue: Double,
                                    val adjacentValue: Double,
                                    val targetTime: DayTime,
                                    val timeDistanceValue: Double)

private val rankingSaver = Saver.create<ScheduleRankingArguments>(File("saves/rankings/"))

class ScheduleRankingPage : Page() {
    override val root = ScrollPane()

    private val creditValue = Spinner<Double>(-Double.MAX_VALUE, Double.MAX_VALUE, 0.0)
    private val adjacentValue = Spinner<Double>(-Double.MAX_VALUE, Double.MAX_VALUE, 0.0)
    private val targetTime = DayTimePicker()
    private val timeDistanceValue = Spinner<Double>(-Double.MAX_VALUE, Double.MAX_VALUE, 0.0)

    init {
        val backButton = Button("Back")
        backButton.onAction = EventHandler { onBack() }

        val nameField = CustomTextField()
        nameField.right = Label("Name")

        // Hate to copy code from CalendarPage
        val saveButton = Button("Save")
        saveButton.onAction = EventHandler {
            val alert = Alert(Alert.AlertType.CONFIRMATION, "", ButtonType.YES, ButtonType.NO)
            alert.showAndWait()

            if (alert.result == ButtonType.YES) {
                rankingSaver.save(nameField.text, getRanking())
            }
        }

        // Hate to copy code from CalendarPage
        val loadButton = Button("Load")
        loadButton.onAction = EventHandler {
            val ranking = rankingSaver.load(nameField.text)
            if (ranking != null) {
                val alert = Alert(Alert.AlertType.CONFIRMATION, "", ButtonType.YES, ButtonType.NO)
                alert.showAndWait()

                if (alert.result == ButtonType.YES) {
                    setRanking(ranking)
                }
            }
        }

        val nextButton = Button("Next")
        nextButton.onAction = EventHandler { onNext() }

        val headerHBox = HBox(backButton, nameField, saveButton, loadButton, nextButton)

        creditValue.isEditable = true
        adjacentValue.isEditable = true
        timeDistanceValue.isEditable = true

        root.content = VBox(headerHBox,
            Label("Credit Value", creditValue),
            Label("Adjacent Value", adjacentValue),
            Label("Target Time", targetTime),
            Label("Minute Distance Value", timeDistanceValue))
    }

    fun getRanking(): ScheduleRankingArguments {
        return ScheduleRankingArguments(creditValue.value,
            adjacentValue.value,
            targetTime.getValue(),
            timeDistanceValue.value)
    }

    private fun setRanking(ranking: ScheduleRankingArguments) {
        creditValue.valueFactory.value = ranking.creditValue
        adjacentValue.valueFactory.value = ranking.adjacentValue
        targetTime.setValue(ranking.targetTime)
        timeDistanceValue.valueFactory.value = ranking.timeDistanceValue
    }
}
