package uy.kohesive.keplin.elasticsearch.kotlinscript

import org.jetbrains.kotlin.cli.common.repl.ScriptArgsWithTypes
import kotlin.reflect.KClass


internal val SCRIPT_ARGS_TYPES: Array<out KClass<out Any>> = arrayOf(Map::class, Map::class, Map::class, Any::class, Double::class)
internal val EMPTY_SCRIPT_ARGS: Array<out Any?> = makeArgs().scriptArgs  // must be after types

internal fun makeArgs(variables: Map<String, Any> = emptyMap(),
                      score: Double = 0.0,
                      doc: MutableMap<String, MutableList<Any>> = hashMapOf(),
                      ctx: MutableMap<Any, Any> = hashMapOf(),
                      value: Any? = null): ScriptArgsWithTypes {
    return ScriptArgsWithTypes(arrayOf(variables, doc, ctx, value, score), SCRIPT_ARGS_TYPES)
}

open class ConcreteEsKotlinScriptTemplate(parm: Map<String, Any>,
                                          doc: MutableMap<String, MutableList<Any>>,
                                          ctx: MutableMap<Any, Any>,
                                          _value: Any?,
                                          _score: Double) : EsKotlinScriptTemplate(parm, doc, ctx, _value, _score)

abstract class EsKotlinScriptTemplate(val parm: Map<String, Any>,
                                      val doc: MutableMap<String, MutableList<Any>>,
                                      val ctx: MutableMap<Any, Any>,
                                      val _value: Any?,
                                      val _score: Double) {

    fun docInt(field: String, default: Int): Int = (doc[field]?.single() as? Long)?.toInt() ?: default
    fun docInt(field: String): Int? = (doc[field]?.single() as? Long)?.toInt()

    fun docString(field: String, default: String): String = (doc[field]?.single() as? String) ?: default
    fun docString(field: String): String? = (doc[field]?.single() as? String)

    @Suppress("UNCHECKED_CAST")
    fun docStringList(field: String): List<String> = (doc[field] as? List<String>) ?: emptyList()

    @Suppress("UNCHECKED_CAST")
    fun docIntList(field: String): List<Int> = (doc[field] as? List<Int>) ?: emptyList()

    fun parmInt(field: String): Int? = parm[field] as? Int
    fun parmInt(field: String, default: Int): Int = parm[field] as? Int ?: default

    fun parmString(field: String): String? = parm[field] as? String
    fun parmString(field: String, default: String): String = parm[field] as? String ?: default
}

fun <V : Any> Map<String, V>?.toWrapped() = this?.map { it.key to EsWrappedValue(it.value) }?.toMap() ?: emptyMap()

fun EsWrappedValue?.asInt(default: Int): Int = this?.asInt() ?: default
fun EsWrappedValue?.asString(default: String): String = this?.asString() ?: default

class EsWrappedValue(val value: Any) {
    fun asString(): String = value as String
    fun asInt(): Int = value as Int
}