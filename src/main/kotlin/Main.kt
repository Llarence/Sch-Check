import javafx.application.Application
import javafx.application.Application.launch
import javafx.application.Platform
import javafx.event.EventHandler
import javafx.scene.Scene
import javafx.scene.layout.VBox
import javafx.scene.text.Text
import javafx.stage.Stage
import kotlinx.coroutines.*
import kotlinx.coroutines.javafx.JavaFx
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicReference
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.minutes

val coroutineScope = CoroutineScope(Dispatchers.Default)
val fxScope = CoroutineScope(Dispatchers.JavaFx)

fun LocalDate.withScheduleWeekday(day: Int): LocalDate {
    return this.plusDays((day + 1).mod(7).toLong() - this.dayOfWeek.value)
}

// TODO: Add calendar name checking for the filename or maybe convert
//  the filename to something valid with regex or something
// TODO: Use apply more
class App : Application() {
    private lateinit var scene: Scene

    private val loadRoot = VBox()

    private val loadText = Text()

    init {
        loadRoot.children.add(loadText)
    }

    private fun transitionToCalendar(argument: ScheduleGenArgument) {
        fxScope.launch {
            val text = AtomicReference<String>()
            loadText.text = text.get()

            // This stops the callback from spamming invoke later
            val textUpdater = fxScope.launch {
                while (true) {
                    delay(100)
                    loadText.text = text.get()
                }
            }

            scene.root = loadRoot

            val schedules = coroutineScope.async {
                genSchedule(argument.classGroups.map { Pair(it, 0.1) },
                    argument.term,
                    40000,
                    genGradeFun(
                        argument.gradeFunGeneratorArguments.breaksAndWeights,
                        argument.gradeFunGeneratorArguments.creditWeight,
                        15.minutes,
                        argument.gradeFunGeneratorArguments.backToBackWeight
                    )) { description, completion ->
                    text.set(String.format("%s: %.2f%%", description, completion * 100))
                }
            }.await()

            textUpdater.cancelAndJoin()

            CalendarSceneManager.loadSchedules(schedules)
            scene.root = CalendarSceneManager.root
        }
    }

    override fun start(stage: Stage) {
        stage.title = "Calendar"
        stage.isMaximized = true
        stage.centerOnScreen()

        stage.setOnCloseRequest {
            Platform.exit()
            exitProcess(0)
        }

        ArgumentSceneManager.doneButton.onAction = EventHandler {
            transitionToCalendar(ArgumentSceneManager.getArgument())
        }

        CalendarSceneManager.backButton.onAction = EventHandler {
            scene.root = ArgumentSceneManager.root
        }

        scene = Scene(ArgumentSceneManager.root)
        stage.scene = scene

        stage.show()
    }
}

fun main() {
    launch(App::class.java)
}
