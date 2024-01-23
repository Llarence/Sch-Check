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

    var onDone = {  }
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

        val scheduleViewer = CalendarPage()

        val termSelector = TermSelectPage()
        termSelector.onDone = {
            val argumentSelector = ArgumentPage(termSelector.getTerm())
            argumentSelector.onDone = {
                scheduleViewer.loadSchedules(listOf())
                scene.root = scheduleViewer.root

                fxScope.launch {
                    val argument = argumentSelector.getArgument()

                    val classGroupsDeferred = argument.classGroupsSearches.map { searches ->
                        searches.map { search -> async {
                            getSearch(search).map { async { convertResponse(it) } } }
                        }
                    }
                    scheduleViewer.loadSchedules(genSchedules(
                        classGroupsDeferred.map { deferreds -> deferreds.flatMap { deferred -> deferred.await().map { it.await() } } },
                        argument.tries,
                        argument.skipChance))
                }
            }

            scene.root = argumentSelector.root
        }

        scene = Scene(termSelector.root)
        scene.stylesheets.add("entry.css")
        stage.scene = scene

        stage.show()
    }
}

fun main() = launch(App::class.java)
