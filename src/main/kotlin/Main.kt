import javafx.application.Application
import javafx.application.Application.launch
import javafx.application.Platform
import javafx.scene.Scene
import javafx.scene.control.ScrollPane
import javafx.scene.layout.VBox
import javafx.stage.Stage
import kotlinx.coroutines.*
import kotlinx.coroutines.javafx.JavaFx
import kotlin.system.exitProcess

val coroutineScope = CoroutineScope(Dispatchers.Default)
val fxScope = CoroutineScope(Dispatchers.JavaFx)

abstract class Page {
    val mainVBox = VBox()
    val root = ScrollPane(mainVBox)

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

        val termSelector = TermSelectPage()
        termSelector.onDone = { scene.root = ArgumentPage(termSelector.getTerm()).root }

        scene = Scene(termSelector.root)
        scene.stylesheets.add("entry.css")
        stage.scene = scene

        stage.show()
    }
}

fun main() = launch(App::class.java)
