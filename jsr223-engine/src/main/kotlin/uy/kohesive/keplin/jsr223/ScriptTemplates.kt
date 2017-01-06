package uy.kohesive.keplin.jsr223

import uy.kohesive.keplin.kotlin.core.scripting.ResettableRepl
import uy.kohesive.keplin.kotlin.core.scripting.ScriptArgsWithTypes
import javax.script.ScriptContext

abstract class KeplinKotlinJsr223ScriptTemplate(val kotlinScript: ResettableRepl, val context: ScriptContext)

val KeplinKotlinJsr223ScriptTemplateArgTypes = arrayOf(ResettableRepl::class, ScriptContext::class)
val KeplinKotlinJsr223ScriptTemplateEmptyArgs
        = ScriptArgsWithTypes(arrayOf<Any?>(null, null), KeplinKotlinJsr223ScriptTemplateArgTypes)