package onj

import java.io.File

class OnjJsonPreprocessor {

    fun preprocess(srcDir: File, outDir: File) {
        outDir.deleteRecursively()

        srcDir
            .walk()
            .filter { it.isDirectory }
            .forEach {
                File("$outDir/${it.relativeTo(srcDir)}").mkdirs()
            }

        srcDir
            .walk()
            .filter { it.extension == "onj" }
            .forEach { file ->
                val obj = OnjParser.parseFile(file)
                val relFile = file.relativeTo(srcDir)
                val path = relFile.parentFile?.path?.let { "$it/" } ?: ""
                val outFile = File("$outDir/$path${relFile.nameWithoutExtension}.json")
                outFile.createNewFile()
                outFile.writeText(obj.toJsonString())
            }
    }
}