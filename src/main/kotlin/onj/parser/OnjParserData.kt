package onj.parser

import onj.schema.OnjSchema
import onj.schema.OnjSchemaNamedObject
import onj.value.OnjValue
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

data class OnjParserData(
    val importBasePath: String? = null,
    val importCache: (file: File) -> OnjValue? = { null }
) {

    internal fun resolvePath(path: String): Path = importBasePath?.let {
        Paths.get(it).resolve(path)
    } ?: Paths.get(path)
}

class OnjSchemaParserData(
    val importBasePath: String? = null,
    val analysisMode: Boolean = false,
    val importCache: (file: File) -> Pair<OnjSchema, MutableMap<String, List<OnjSchemaNamedObject>>>? = { null }
) {

    internal fun resolvePath(path: String): Path = importBasePath?.let {
        Paths.get(it).resolve(path)
    } ?: Paths.get(path)
}
