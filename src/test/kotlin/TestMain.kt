import onj.*
import java.io.File

fun main() {//TODO: proper tests

    OnjJsonPreprocessor().preprocess(File("src/test/res/onj"), File("src/test/res/json"))

}
