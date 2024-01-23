import javafx.event.EventHandler
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.scene.layout.HBox

class SaveLoadPanel : HBox() {
    private val filePath = TextField()

    var onSave: (String) -> Unit = {  }
    var onLoad: (String) -> Unit = {  }

    init {
        val save = Button("Save")
        save.onAction = EventHandler {
            onSave(filePath.text)
        }

        val load = Button("Load")
        load.onAction = EventHandler {
            onLoad(filePath.text)
        }

        children.addAll(save, load, Label("Name", filePath))
    }
}
