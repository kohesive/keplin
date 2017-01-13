package uy.kohesive.keplin.jsr223

import uy.kohesive.keplin.jsr223.core.scripting.AbstractEngineFactory
import javax.script.ScriptEngine

open class CompilableEngineFactory : AbstractEngineFactory() {
    override fun getScriptEngine(): ScriptEngine {
        return CompilableEngine(this).apply { fixupArgsForScriptTemplate() }
    }

    override fun getEngineName(): String = "Keplin Kotlin Compilable Scripting Engine"
    override fun getNames(): List<String> = listOf("keplin-kotin-compilable")
    override fun getThreadingModel(): String = "MULTITHREADED"
}