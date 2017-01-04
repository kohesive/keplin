package uy.kohesive.keplin.kotlin.core.scripting

import org.jetbrains.kotlin.script.KotlinScriptDefinition
import kotlin.reflect.KClass

// TODO: can auto detect script arg types from constructor of template?

open class KotlinScriptDefinitionWithArgsInfo(template: KClass<out Any>,
                                              val defaultEmptyArgs: ScriptArgsWithTypes?) : KotlinScriptDefinition(template)

class ScriptArgsWithTypes(val scriptArgs: Array<out Any?>?, val scriptArgsTypes: Array<out KClass<out Any>>?)