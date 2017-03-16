package uy.kohesive.keplin.elasticsearch.kotlinscript

import org.jetbrains.kotlin.cli.common.repl.ScriptArgsWithTypes
import kotlin.reflect.KClass


internal val SCRIPT_ARGS_TYPES: Array<out KClass<out Any>> = arrayOf(Map::class, Map::class, Map::class, Any::class, Double::class)
internal val EMPTY_SCRIPT_ARGS: Array<out Any?> = makeArgs().scriptArgs  // must be after types

internal fun makeArgs(variables: Map<String, EsWrappedValue> = emptyMap(),
                      score: Double = 0.0,
                      doc: MutableMap<String, List<Any>> = hashMapOf(),
                      ctx: Map<Any, Any> = emptyMap(),
                      value: Any? = null): ScriptArgsWithTypes {
    return ScriptArgsWithTypes(arrayOf(variables, doc, ctx, value, score), SCRIPT_ARGS_TYPES)
}


abstract class EsKotlinScriptTemplate(val parm: Map<String, EsWrappedValue>,
                                      val doc: MutableMap<String, List<Any>>,
                                      val ctx: Map<Any, Any>,
                                      val _value: Any?,
                                      val _score: Double) {

    fun docInt(field: String, default: Int): Int = (doc[field]?.single() as? Long)?.toInt() ?: default
    fun parmInt(field: String): Int? = parm[field]?.asInt()
    fun parmInt(field: String, default: Int): Int = parm[field].asInt(default)

    fun parmString(field: String): String? = parm[field]?.asString()
    fun parmString(field: String, default: String): String = parm[field].asString(default)

    fun EsWrappedValue?.asInt(default: Int): Int = this?.asInt() ?: default
    fun EsWrappedValue?.asString(default: String): String = this?.asString() ?: default
}

fun <V : Any> Map<String, V>?.toWrapped() = this?.map { it.key to EsWrappedValue(it.value) }?.toMap() ?: emptyMap()

class EsWrappedValue(val value: Any) {
    fun asString(): String = value as String
    fun asInt(): Int = value as Int
}