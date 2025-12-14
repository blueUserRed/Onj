package onj.parser

import onj.schema.OnjSchema
import onj.schema.OnjSchemaNamedObject
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

data class OnjParserData<T>(
    val importBasePath: String? = null,
    val importCache: (file: File) -> T? = { null }
) {

    internal fun resolvePath(path: String): Path = importBasePath?.let {
        Paths.get(it).resolve(path)
    } ?: Paths.get(path)
}

typealias OnjSchemaParserData = OnjParserData<Pair<OnjSchema, MutableMap<String, List<OnjSchemaNamedObject>>>>
