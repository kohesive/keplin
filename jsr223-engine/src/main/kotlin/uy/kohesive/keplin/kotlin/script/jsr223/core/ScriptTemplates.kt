package uy.kohesive.keplin.kotlin.script.jsr223.core

import org.jetbrains.kotlin.cli.common.repl.ScriptArgsWithTypes
import uy.kohesive.keplin.kotlin.script.SimplifiedRepl
import javax.script.ScriptContext

abstract class KeplinKotlinJsr223ScriptTemplate(val kotlinScript: SimplifiedRepl, val context: ScriptContext)

val KeplinKotlinJsr223ScriptTemplateArgTypes = arrayOf(SimplifiedRepl::class, ScriptContext::class)
val KeplinKotlinJsr223ScriptTemplateEmptyArgs
        = ScriptArgsWithTypes(arrayOf<Any?>(null, null), KeplinKotlinJsr223ScriptTemplateArgTypes)