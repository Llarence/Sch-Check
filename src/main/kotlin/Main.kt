import javafx.application.Application
import javafx.application.Application.launch
import javafx.application.Platform
import javafx.scene.Scene
import javafx.scene.layout.VBox
import javafx.scene.text.Text
import javafx.stage.Stage
import kotlinx.coroutines.*
import kotlinx.coroutines.javafx.JavaFx
import java.io.File
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicReference
import kotlin.system.exitProcess

val coroutineScope = CoroutineScope(Dispatchers.Default)
val fxScope = CoroutineScope(Dispatchers.JavaFx)

val calendarSaver = Saver(File("saves/calendars/"))

fun LocalDate.withScheduleWeekday(day: Int): LocalDate {
    return this.plusDays((day + 1).mod(7).toLong() - this.dayOfWeek.value)
}

// TODO: Add calendar name checking for the filename or maybe convert
//  the filename to something valid with regex or something
class App : Application() {
    private lateinit var stage: Stage

    private val loadScene: Scene

    private val loadText = Text()

    init {
        val loadVBox = VBox()

        loadVBox.children.add(loadText)

        loadScene = Scene(loadVBox)
    }

    override fun start(stage: Stage) {
        this.stage = stage

        stage.title = "Calendar"
        stage.isMaximized = true
        stage.centerOnScreen()

        stage.setOnCloseRequest {
            Platform.exit()
            exitProcess(0)
        }

        fxScope.launch {
            val text = AtomicReference<String>()
            loadText.text = text.get()

            val textUpdater = fxScope.launch {
                while (true) {
                    delay(100)
                    loadText.text = text.get()
                }
            }

            stage.scene = loadScene

            val schedules = coroutineScope.async {
                genSchedule(listOf(
                    Pair(listOf("MTH 311"), 0.1),
                    Pair(listOf("MTH 343"), 0.1),
                    Pair(listOf("MTH 351"), 0.1),
                    Pair(listOf("COMM 114"), 0.1),
                    Pair(listOf("ENGR 103"), 0.1),
                    Pair(listOf("PH 211"), 0.1),
                    Pair(listOf("ANTH 284", "BI 102", "BI 103", "BI 204"), 0.1),
                    Pair(listOf("ED 216", "ED 219", "ENG 220", "ENG 260"), 0.1),
                    Pair(listOf("ATS 342", "GEO 308", "GEOG 300", "H 312"), 0.1),
                    Pair(listOf("ANTH 330", "ART 367", "ES 319", "FES 485"), 0.1)
                ), Term.WINTER,
                    400000,
                    genGradeFun(listOf(), 0.0, 1.0)) { description, percent ->
                    text.set(String.format("%s: %.2f%%", description, percent * 100))
                }
            }.await()

            textUpdater.cancelAndJoin()

            stage.scene = CalendarSceneManager.loadScene(schedules)
        }

        stage.show()
    }
}

fun main() {
    launch(App::class.java)
}
