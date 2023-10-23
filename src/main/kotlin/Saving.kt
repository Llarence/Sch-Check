import java.io.File
import java.io.IOException

class Saver(private val directory: File) {
    init {
        if (!directory.isDirectory) {
            if (!directory.mkdir()) {
                throw IOException()
            }
        }
    }

    fun save(name: String, schedule: Schedule) {

    }

    fun load() {

    }
}
