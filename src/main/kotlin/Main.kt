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
        val rankingSelector = ScheduleRankingPage()
        termSelector.onNext = {
            val scheduleGenSelector = ScheduleGenPage(termSelector.getTerm())

            // Could have it start doing the requests on the transition to the rankingSelector
            scheduleGenSelector.onNext = {
                scene.root = rankingSelector.root

                rankingSelector.onNext = {
                    scene.root = loading.root

                    scheduleViewer.onBack = {
                        scene.root = rankingSelector.root
                    }

                    transitionToCalendar(scheduleViewer,
                        loading,
                        scheduleGenSelector.getArgument(),
                        rankingSelector.getRanking())
                }

                rankingSelector.onBack = {
                    scene.root = scheduleGenSelector.root
                }
            }

            scheduleGenSelector.onBack = {
                scene.root = termSelector.root
            }

            scene.root = scheduleGenSelector.root
        }

        scene = Scene(termSelector.root)
        scene.stylesheets.add("entry.css")
        stage.scene = scene

        stage.show()
    }

    private fun transitionToCalendar(scheduleViewer: CalendarPage,
                                     loadingPage: LoadingPage,
                                     genArguments: ScheduleGenArguments,
                                     rankingArguments: ScheduleRankingArguments) {
        fxScope.launch {
            // TODO: Just pass genArguments to genSchedules and have classGroupsSearches by outside of it
            val classSearchesDeferred = genArguments.classGroupsSearches.map { searches ->
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
                genArguments.tries,
                genArguments.skipChance).sortedBy { -valueSchedule(it, rankingArguments) }
            )

            scene.root = scheduleViewer.root
        }
    }
}

fun main() = launch(App::class.java)
