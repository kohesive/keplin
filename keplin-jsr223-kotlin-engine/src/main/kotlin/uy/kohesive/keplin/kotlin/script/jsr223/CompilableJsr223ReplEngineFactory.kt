package uy.kohesive.keplin.kotlin.script.jsr223

import uy.kohesive.keplin.kotlin.script.jsr223.core.AbstractEngineFactory
import javax.script.ScriptEngine


open class CompilableJsr223ReplEngineFactory : AbstractEngineFactory() {
    override fun getScriptEngine(): ScriptEngine {
        return CompilableJsr223ReplEngine(this).apply { fixupArgsForScriptTemplate() }
    }

    override fun getEngineName(): String = "Kotlin REPL Compilable Scripting Engine"
    override fun getNames(): List<String> = listOf(jsr223EngineName)
    override fun getThreadingModel(): String = "MULTITHREADED"

    companion object {
        val jsr223EngineName = CompilableJsr223ReplEngine.jsr223EngineName
    }
}