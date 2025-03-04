import javafx.application.Application
import javafx.application.Application.launch
import javafx.application.Platform
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.stage.Stage
import kotlinx.coroutines.*
import kotlinx.coroutines.javafx.JavaFx
import java.time.Instant
import java.time.format.DateTimeFormatter
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

// TODO: add oring of "enum" fields (ie you could pick ecampus or corvallis)
suspend fun main() = launch(App::class.java)//check()

suspend fun check() {
    while (true) {
        try {
            val response = cachedRequest(
                "https://prodapps.isadm.oregonstate.edu/StudentRegistrationSsb/ssb/searchResults/" +
                        "searchResults?txt_subject=CS&txt_courseNumber=361&txt_campus=C&txt_term=202501&pageMaxSize=500",
                "202501",
                // Somehow the request is malformed in a way that once
                //  it gets one it returns the same things over and over
                true,
                cache = null
            )

            val decodedResponse = json.decodeFromString<SearchResponse>(response)
            print(DateTimeFormatter.ISO_INSTANT.format(Instant.now()))
            print(" ")
            println(decodedResponse)

            if (decodedResponse.data.any { it.seatsAvailable > 0 }) {
                withContext(Dispatchers.IO) {
                    ProcessBuilder("notify-send", "Found1").start()
                }
            }
        } catch (_: Exception) {
            withContext(Dispatchers.IO) {
                ProcessBuilder("notify-send", "Error1").start()
            }
        }

        withContext(Dispatchers.IO) {
            Thread.sleep(60 * 1000)
        }
    }
}
