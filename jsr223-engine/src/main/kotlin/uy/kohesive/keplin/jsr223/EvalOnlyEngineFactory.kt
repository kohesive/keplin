package uy.kohesive.keplin.jsr223

import uy.kohesive.keplin.jsr223.core.scripting.AbstractEngineFactory
import javax.script.ScriptEngine

open class EvalOnlyEngineFactory : AbstractEngineFactory() {
    override fun getScriptEngine(): ScriptEngine {
        return EvalOnlyEngine(this).apply { fixupArgsForScriptTemplate() }
    }

    override fun getEngineName(): String = "Keplin Kotlin Eval-Only Scripting Engine"
    override fun getNames(): List<String> = listOf("keplin-kotin")
    override fun getThreadingModel(): String = "MULTITHREADED"
}