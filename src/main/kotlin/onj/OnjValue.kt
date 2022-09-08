package onj

import kotlin.reflect.KClass

abstract class OnjValue {

    abstract val value: Any?

    fun isInt(): Boolean = this is OnjInt
    fun isNull(): Boolean = this is OnjNull
    fun isFloat(): Boolean = this is OnjFloat
    fun isString(): Boolean = this is OnjString
    fun isOnjArray(): Boolean = this is OnjArray
    fun isBoolean(): Boolean = this is OnjBoolean
    fun isOnjObject(): Boolean = this is OnjObject

    abstract override fun toString(): String
    abstract fun toString(indentationLevel: Int): String

    abstract fun toJsonString(): String
    abstract fun toJsonString(indentationLevel: Int): String
}

class OnjInt(override val value: Long) : OnjValue() {

    override fun toString(): String = toString(0)

    override fun toString(indentationLevel: Int): String {
        return value.toString()
    }



    override fun toJsonString(indentationLevel: Int) = value.toString()
    override fun toJsonString(): String = value.toString()
}

class OnjFloat(override val value: Double) : OnjValue() {

    override fun toString(): String = toString(0)

    override fun toString(indentationLevel: Int): String {
        return if (value == Double.POSITIVE_INFINITY) "Pos_Infinity"
        else if (value == Double.NEGATIVE_INFINITY) "Neg_Infinity"
        else if (value.isNaN()) "NaN"
        else value.toString()
    }

    override fun toJsonString(indentationLevel: Int): String {
        return if (value == Double.POSITIVE_INFINITY) "Infinity"
        else if (value == Double.NEGATIVE_INFINITY) "-Infinity"
        else if (value.isNaN()) "NaN"
        else value.toString()
    }

    override fun toJsonString(): String = toJsonString(0)
}

class OnjString(override val value: String) : OnjValue() {

    override fun toString(indentationLevel: Int): String = "'$value'"
    override fun toString(): String = "'$value'"
    override fun toJsonString(): String = "\"$value\""
    override fun toJsonString(indentationLevel: Int): String = "\"$value\""
}

class OnjBoolean(override val value: Boolean) : OnjValue() {

    override fun toString(): String = value.toString()
    override fun toString(indentationLevel: Int): String = value.toString()
    override fun toJsonString(): String = value.toString()
    override fun toJsonString(indentationLevel: Int): String = value.toString()
}

class OnjNull : OnjValue() {

    override val value: Any? = null

    override fun toString(): String = "null"
    override fun toString(indentationLevel: Int): String = "null"
    override fun toJsonString(): String = "null"
    override fun toJsonString(indentationLevel: Int): String = "null"
}

class OnjObject(override val value: Map<String, OnjValue>) : OnjValue() {

    override fun toString(): String {
        val builder = StringBuilder()
        for (entry in value.entries) {
            builder
                .append("${entry.key}: ")
                .append(entry.value.toString(1))
                .append("\n")
        }
        return builder.toString()
    }

    override fun toString(indentationLevel: Int): String {
        val builder = StringBuilder()
        builder.append("{")
        builder.append("\n")
        for (entry in value.entries) {
            for (i in 1..indentationLevel) builder.append("    ")
            builder
                .append("${entry.key}: ")
                .append(entry.value.toString(indentationLevel + 1))
                .append("\n")
        }
        for (i in 1 until indentationLevel) builder.append("    ")
        builder.append("}")
        return builder.toString()
    }

    override fun toJsonString(indentationLevel: Int): String {
        val builder = StringBuilder()
        builder.append("{")
        builder.append("\n")
        val entries = value.entries.toList()
        for (i in entries.indices) {
            for (x in 1..indentationLevel) builder.append("    ")
            builder
                .append("\"${entries[i].key}\": ")
                .append(entries[i].value.toJsonString(indentationLevel + 1))
            if (i != entries.size - 1) builder.append(",")
            builder.append("\n")
        }
        for (i in 1 until indentationLevel) builder.append("    ")
        builder.append("}")
        return builder.toString()
    }

    override fun toJsonString(): String = toJsonString(0)

    operator fun get(identifier: String): OnjValue? = value[identifier]

    inline fun <reified T> hasKey(key: String): Boolean {
        if (!value.containsKey(key)) return false
        return value[key]?.value is T
    }

    fun hasKeys(keys: Map<String, KClass<*>>): Boolean {
        for (key in keys) {
            if (!key.value.isInstance(value[key.key]?.value)) return false
        }
        return true
    }

    inline fun <reified T> get(key: String): T {
        return if (value[key]?.value is T) value[key]?.value as T else value[key] as T
    }

}

class OnjArray(override val value: List<OnjValue>) : OnjValue() {

    override fun toString(): String = toString(0)

    override fun toString(indentationLevel: Int): String {
        val builder = StringBuilder()
        if (shouldInline()) {
            builder.append("[ ")
            for (part in value) {
                builder
                    .append(part.toString(indentationLevel + 1))
                    .append(" ")
            }
            builder.append("]")
            return builder.toString()
        }

        builder
            .append("[")
            .append("\n")
        for (part in value) {
            for (i in 1..indentationLevel) builder.append("    ")
            builder
                .append(part.toString(indentationLevel + 1))
                .append("\n")
        }
        for (i in 1 until indentationLevel) builder.append("    ")
        builder.append("]")
        return builder.toString()
    }

    override fun toJsonString(indentationLevel: Int): String {
        val builder = StringBuilder()
        if (shouldInline()) {
            builder.append("[ ")
            for (i in value.indices) {
                builder.append(value[i].toJsonString(indentationLevel + 1))
                if (i != value.size - 1) builder.append(", ")
            }
            builder.append(" ]")
            return builder.toString()
        }

        builder
            .append("[")
            .append("\n")

        for (i in value.indices) {
            for (x in 1..indentationLevel) builder.append("    ")
            builder.append(value[i].toJsonString(indentationLevel + 1))
            if (i != value.size - 1) builder.append(",")
            builder.append("\n")
        }
        for (i in 1 until indentationLevel) builder.append("    ")
        builder.append("]")
        return builder.toString()
    }

    override fun toJsonString() = toJsonString(0)

    inline fun <reified T> hasOnlyType(): Boolean {
        for (part in value) if (part.value !is T) return false
        return true
    }

    operator fun get(index: Int): OnjValue = value[index]

    fun size(): Int = value.size

    inline fun <reified T> get(index: Int): T = if (value[index].value is T) value[index].value as T else value[index] as T

    private fun shouldInline(): Boolean {
        //TODO: this is stupid
        if (value.isEmpty()) return true
        var cost = 0
        for (part in value) {
            if (part.isOnjArray() || part.isOnjObject()) return false
            else if (part.isBoolean()) cost += 5
            else if (part.isInt() || part.isFloat()) cost += 4
            else if (part.isString()) cost += (part as OnjString).value.length
            else if (part.isNull()) cost += 4
        }
        return cost < 60
    }
}
