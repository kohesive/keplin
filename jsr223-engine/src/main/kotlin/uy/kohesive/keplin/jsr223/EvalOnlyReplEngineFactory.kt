package uy.kohesive.keplin.jsr223

import uy.kohesive.keplin.jsr223.core.scripting.AbstractEngineFactory
import javax.script.ScriptEngine

open class EvalOnlyReplEngineFactory : AbstractEngineFactory() {
    override fun getScriptEngine(): ScriptEngine {
        return EvalOnlyJsr223ReplEngine(this).apply { fixupArgsForScriptTemplate() }
    }

    override fun getEngineName(): String = "Keplin Kotlin Eval-Only Scripting Engine"
    override fun getNames(): List<String> = listOf(jsr223EngineName)
    override fun getThreadingModel(): String = "MULTITHREADED"

    companion object {
        val jsr223EngineName = EvalOnlyJsr223ReplEngine.jsr223EngineName
    }
}