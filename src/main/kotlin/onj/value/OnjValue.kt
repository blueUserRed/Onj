package onj.value

import onj.parser.OnjParserException
import kotlin.reflect.KClass
import kotlin.reflect.full.cast

/**
 * represents a part of the parsed onj-structure
 */
abstract class OnjValue {

    /**
     * the actual value of the element as a kotlin datatype
     */
    abstract val value: Any?

    fun isInt(): Boolean = this is OnjInt
    fun isNull(): Boolean = this is OnjNull
    fun isFloat(): Boolean = this is OnjFloat
    fun isString(): Boolean = this is OnjString
    fun isOnjArray(): Boolean = this is OnjArray
    fun isBoolean(): Boolean = this is OnjBoolean
    fun isOnjObject(): Boolean = this is OnjObject


    override fun equals(other: Any?): Boolean {
        return other != null && other::class == this::class && (other as OnjValue).value == this.value
    }

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String {
        val info = ToStringInformation(json = false, minified = false)
        stringify(info)
        return info.builder.toString()
    }

    abstract fun stringify(info: ToStringInformation)

    data class ToStringInformation(
        val indent: String = "    ",
        val json: Boolean,
        val minified: Boolean,
        val indentationLevel: Int = 0,
        val builder: StringBuilder = StringBuilder()
    ) {
        fun withIndentationLevel(indentationLevel: Int) = ToStringInformation(
            indent, json, minified, indentationLevel, builder
        )
    }

}

/**
 * represents an Int (kotlin type is [Long])
 */
class OnjInt(override val value: Long) : OnjValue() {

    override fun stringify(info: ToStringInformation) {
        info.builder.append(value.toString())
    }

}

/**
 * represents a Float (kotlin type is [Double])
 */
class OnjFloat(override val value: Double) : OnjValue() {

    override fun stringify(info: ToStringInformation) {
        info.builder.append(if (!info.json) {
            when (value) {
                Double.POSITIVE_INFINITY -> "infinity"
                Double.NEGATIVE_INFINITY -> "-infinity"
                else -> if (value.isNaN()) "NaN" else value.toString()
            }
        } else {
            when (value) {
                Double.POSITIVE_INFINITY -> "Infinity"
                Double.NEGATIVE_INFINITY -> "-Infinity"
                else -> if (value.isNaN()) "NaN" else value.toString()
            }
        })
    }

}

/**
 * represents a String
 */
class OnjString(override val value: String) : OnjValue() {

    override fun stringify(info: ToStringInformation) {
        val cleaned = value
            .replace("\n", "\\n")
            .replace("\r", "\\r")
        info.builder.append(if (info.json) "\"$cleaned\"" else "'$cleaned'")
    }

}

/**
 * represents a Boolean
 */
class OnjBoolean(override val value: Boolean) : OnjValue() {

    override fun stringify(info: ToStringInformation) {
        info.builder.append(value.toString())
    }
}

/**
 * represents a null-value
 */
class OnjNull : OnjValue() {

    override val value: Any? = null

    override fun stringify(info: ToStringInformation) {
        info.builder.append("null")
    }
}

/**
 * represents an object (kotlin type is [Map])
 */
open class OnjObject(override val value: Map<String, OnjValue>) : OnjValue() {

    override fun toString(): String {
        val info = ToStringInformation(json = false, minified = false)
        for (entry in value.entries) {
            val key = if (isValidKey(entry.key)) entry.key else "'${entry.key}'"
            info.builder.append("$key: ")
            entry.value.stringify(info.withIndentationLevel(1))
            info.builder.append(",\n")
        }
        return info.builder.toString()
    }

    fun toMinifiedString(): String {
        val info = ToStringInformation(json = false, minified = true)
        for (entry in value.entries) {
            val key = if (isValidKey(entry.key)) entry.key else "'${entry.key}'"
            info.builder.append("$key:")
            entry.value.stringify(info.withIndentationLevel(1))
            info.builder.append(",")
        }
        return info.builder.toString()
    }

    fun toJsonString(): String {
        val info = ToStringInformation(json = true, minified = false)
        stringify(info)
        return info.builder.toString()
    }

    fun toMinifiedJsonString(): String {
        val info = ToStringInformation(json = true, minified = true)
        stringify(info)
        return info.builder.toString()
    }

    override fun stringify(info: ToStringInformation) {
        if (info.json) toJsonString(info) else toOnjString(info)
    }

    private fun toOnjString(info: ToStringInformation): String {
        val (indent, _, minified, indentationLevel, builder) = info
        builder.append("{")
        if (!minified) builder.append("\n")
        val entries = value.entries.toList()
        for (i in entries.indices) {
            val entry = entries[i]
            if (!minified) repeat(indentationLevel) {
                builder.append(indent)
            }
            val key = if (isValidKey(entry.key)) entry.key else "'${entry.key}'"
            builder.append(if (minified) "$key:" else "$key: ")
            entry.value.stringify(info.withIndentationLevel(indentationLevel + 1))
            if (!info.minified || i != entries.size - 1) builder.append(if (minified) "," else ",\n")
        }
        if (!minified) {
            for (i in 1 until indentationLevel) builder.append(indent)
        }
        builder.append("}")
        return builder.toString()
    }

    private fun toJsonString(info: ToStringInformation): String {
        val (indent, _, minified, indentationLevel, builder) = info
        builder.append("{")
        if (!minified) builder.append("\n")
        val entries = value.entries.toList()
        for (i in entries.indices) {
            if (!minified) {
                for (x in 1..indentationLevel) builder.append(indent)
            }
            val cleanKey = entries[i].key
                .replace("\n", "")
                .replace("\r", "")
            builder.append("\"$cleanKey\": ")
            entries[i].value.stringify(info.withIndentationLevel(indentationLevel + 1))
            if (i != entries.size - 1) builder.append(",")
            if (!minified) builder.append("\n")
        }
        if (!minified) {
            for (i in 1 until indentationLevel) builder.append(indent)
        }
        builder.append("}")
        return builder.toString()
    }

    operator fun get(identifier: String): OnjValue? = value[identifier]

    /**
     * checks if the object has a key named [key] that has the kotlin or onj type [T]
     */
    inline fun <reified T> hasKey(key: String): Boolean {
        if (!value.containsKey(key)) return false
        return value[key] is T || value[key]?.value is T
    }

    /**
     * checks if the object contains all keys, where String is the name of the key and KClass the
     * (kotlin!) type of the key
     */
    fun hasKeys(keys: Map<String, KClass<*>>): Boolean {
        for (key in keys) {
            if (!key.value.isInstance(value[key.key]?.value)) return false
        }
        return true
    }

    /**
     * gets a value from the object with the key [key] and the type [T].
     * The type can either be the Onj-type or the kotlin-type
     * @throws ClassCastException if the [key] or the type is incorrect
     */
    inline fun <reified T> get(key: String): T {
        return if (value[key]?.value is T) value[key]?.value as T else value[key] as T
    }

    /**
     * checks if the object has the key [key] with type [T] and returns its value. If no such key exists, [or] is
     * returned instead
     */
    inline fun <reified T> getOr(key: String, or: T): T {
        // I hate this code
        return if (value[key]?.value is T) value[key]?.value as T else if (value[key] is T) value[key] as T else or
    }

    /**
     * checks if a key [key] with type [T] exists and if it does, executes [then]. returns the value of [then] or null
     * if it wasn't called
     */
    inline fun <reified T, U> ifHas(key: String, then: (value: T) -> U): U? {
        return if (hasKey<T>(key)) then(value[key] as T) else null
    }

    /**
     * accesses a child of this object using [accessor] in the format `.key.0.otherKey`. The example would access
     * the key 'key' which is expected to be an array, then the index 0 is accessed, which is expected to be an object,
     * and finally, 'otherKey' is accessed. The result (or its [value]) is expected to be of type [T]
     */
    inline fun <reified T : Any> access(accessor: String): T = access(accessor, T::class)

    /**
     * accesses a child of this object using [accessor] in the format `.key.0.otherKey`. The example would access
     * the key 'key' which is expected to be an array, then the index 0 is accessed, which is expected to be an object,
     * and finally, 'otherKey' is accessed. The result (or its [value]) is expected to be of type [toAccess]
     */
    fun <T : Any> access(accessor: String, toAccess: KClass<T>): T {
        if (!accessor.startsWith(".")) throw OnjParserException("accessor must start with '.'")
        val parts = accessor.split(".")
        var cur: OnjValue = this
        parts
            .drop(1)
            .forEach {
                cur = try {
                    val num = Integer.parseInt(it)
                    if (cur !is OnjArray) {
                        throw OnjParserException("tried to access value of type ${cur::class.simpleName} with number")
                    }
                    (cur as OnjArray)[num]
                } catch (e: java.lang.NumberFormatException) {
                    if (cur !is OnjObject) {
                        throw OnjParserException("tried to access value of type ${cur::class.simpleName} with string")
                    }
                    (cur as OnjObject)[it] ?: throw OnjParserException("no key with name $it")
                }
            }
        if (toAccess.isInstance(cur)) return toAccess.cast(cur)
        if (toAccess.isInstance(cur.value)) return toAccess.cast(cur.value)
        throw OnjParserException(
            "value accessed with $accessor was expected to be ${toAccess.simpleName} but is ${cur::class.simpleName}"
        )
    }

    private fun isValidKey(key: String): Boolean {
        return keyRegex.matches(key)
    }

    private companion object {
        val keyRegex = Regex("[a-zA-z_][a-zA-z\\d_]*")
    }
}

/**
 * represents an Array (kotlin type is [List])
 */
class OnjArray(override val value: List<OnjValue>) : OnjValue() {

    override fun stringify(info: ToStringInformation) {
        if (info.json) toJsonString(info) else toOnjString(info)
    }

    private fun toOnjString(info: ToStringInformation) {
        val (indent, _, minified, indentationLevel, builder) = info
        builder.append("[")
        if (!minified) builder.append("\n")
        for (i in value.indices) {
            val part = value[i]
            if (!minified) repeat(indentationLevel) {
                builder.append(indent)
            }
            part.stringify(info.withIndentationLevel(indentationLevel + 1))
            if (!minified || i != value.size - 1) builder.append(if (minified) "," else ",\n")
        }
        if (!minified) {
            for (i in 1 until indentationLevel) builder.append(indent)
        }
        builder.append("]")
    }

    private fun toJsonString(info: ToStringInformation): String {
        val (indent, _, minified, indentationLevel, builder) = info
        builder.append("[")
        if (!minified) builder.append("\n")

        for (i in value.indices) {
            if (!minified) {
                for (x in 1..indentationLevel) builder.append(indent)
            }
            value[i].stringify(info.withIndentationLevel(indentationLevel + 1))
            if (i != value.size - 1) builder.append(",")
            if (!minified) builder.append("\n")
        }
        if (!minified) {
            for (i in 1 until indentationLevel) builder.append(indent)
        }
        builder.append("]")
        return builder.toString()
    }

    /**
     * checks if the array only contains values of type [T]
     */
    inline fun <reified T> hasOnlyType(): Boolean {
        for (part in value) if (part.value !is T) return false
        return true
    }

    /**
     * @return the OnjValue at [index]
     * @throws IndexOutOfBoundsException
     */
    operator fun get(index: Int): OnjValue = value[index]

    /**
     * the size of the array
     */
    fun size(): Int = value.size


    /**
     * accesses a child of this object using [accessor] in the format `.key.0.otherKey`. The example would access
     * the key 'key' which is expected to be an array, then the index 0 is accessed, which is expected to be an object,
     * and finally, 'otherKey' is accessed. The result (or its [value]) is expected to be of type [T]
     */
    inline fun <reified T : Any> access(accessor: String): T = access(accessor, T::class)

    /**
     * accesses a child of this object using [accessor] in the format `.key.0.otherKey`. The example would access
     * the key 'key' which is expected to be an array, then the index 0 is accessed, which is expected to be an object,
     * and finally, 'otherKey' is accessed. The result (or its [value]) is expected to be of type [toAccess]
     */
    fun <T : Any> access(accessor: String, toAccess: KClass<T>): T {
        if (!accessor.startsWith(".")) throw OnjParserException("accessor must start with '.'")
        val parts = accessor.split(".")
        var cur: OnjValue = this
        parts
            .drop(1)
            .forEach {
                cur = try {
                    val num = Integer.parseInt(it)
                    if (cur !is OnjArray) {
                        throw OnjParserException("tried to access value of type ${cur::class.simpleName} with number")
                    }
                    (cur as OnjArray)[num]
                } catch (e: java.lang.NumberFormatException) {
                    if (cur !is OnjObject) {
                        throw OnjParserException("tried to access value of type ${cur::class.simpleName} with string")
                    }
                    (cur as OnjObject)[it] ?: throw OnjParserException("no key with name $it")
                }
            }
        if (toAccess.isInstance(cur)) return toAccess.cast(cur)
        if (toAccess.isInstance(cur.value)) return toAccess.cast(cur.value)
        throw OnjParserException(
            "value accessed with $accessor was expected to be ${toAccess.simpleName} but is ${cur::class.simpleName}"
        )
    }

    /**
     * gets the value at [index] with type [T] where the type can either be the kotlin-type or the onj-type
     * @throws ClassCastException if the index or the type is incorrect
     */
    inline fun <reified T> get(index: Int): T = if (value[index].value is T) value[index].value as T else value[index] as T

}

class OnjNamedObject(val name: String, value: Map<String, OnjValue>) : OnjObject(value) {

    override fun stringify(info: ToStringInformation) {
        if (info.json) {
            super.stringify(info)
            return
        }
        info.builder.append("\$$name")
        if (!info.minified) info.builder.append(" ")
        super.stringify(info)
    }
}
