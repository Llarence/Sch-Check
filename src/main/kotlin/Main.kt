import javafx.application.Application
import javafx.application.Application.launch
import javafx.application.Platform
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.stage.Stage
import kotlinx.coroutines.*
import kotlinx.coroutines.javafx.JavaFx
import kotlin.system.exitProcess

val fxScope = CoroutineScope(Dispatchers.JavaFx)

abstract class Page {
    abstract val root: Parent

    var onBack = {  }
    var onNext = {  }
}

// TODO: Add calendar name checking for the filename or maybe convert
//  the filename to something valid with regex or something
// TODO: Use apply more
class App : Application() {
    private lateinit var scene: Scene

    override fun start(stage: Stage) {
        stage.title = "Calendar"
        stage.isMaximized = true
        stage.centerOnScreen()

        stage.setOnCloseRequest {
            Platform.exit()
            exitProcess(0)
        }

        val loading = LoadingPage()
        val scheduleViewer = CalendarPage()

        val termSelector = TermSelectPage()
        termSelector.onNext = {
            val argumentSelector = ArgumentPage(termSelector.getTerm())
            argumentSelector.onNext = {
                scene.root = loading.root

                scheduleViewer.onBack = {
                    scene.root = argumentSelector.root
                }

                val argument = argumentSelector.getArgument()
                transitionToCalendar(scheduleViewer, loading, argument)
            }

            argumentSelector.onBack = {
                scene.root = termSelector.root
            }

            scene.root = argumentSelector.root
        }

        scene = Scene(termSelector.root)
        scene.stylesheets.add("entry.css")
        stage.scene = scene

        stage.show()
    }

    private fun transitionToCalendar(scheduleViewer: CalendarPage, loadingPage: LoadingPage, argument: ScheduleGenArguments) {
        fxScope.launch {
            val classSearchesDeferred = argument.classGroupsSearches.map { searches ->
                searches.map { search -> async {
                    val response = getSearch(search).map { async {
                        val converted = convertResponse(it)
                        loadingPage.stepDots()
                        converted
                    } }

                    loadingPage.stepDots()
                    response
                } }
            }

            val classGroups = classSearchesDeferred.map { deferreds -> deferreds.flatMap { deferred ->
                deferred.await().map { it.await() } }
            }

            scheduleViewer.loadSchedules(genSchedules(
                classGroups,
                argument.tries,
                argument.skipChance))

            scene.root = scheduleViewer.root
        }
    }
}

fun main() = launch(App::class.java)
