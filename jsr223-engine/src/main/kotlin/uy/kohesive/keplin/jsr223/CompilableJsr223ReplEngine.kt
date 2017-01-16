package uy.kohesive.keplin.jsr223

import uy.kohesive.keplin.jsr223.core.scripting.AbstractCompilableScriptEngine
import uy.kohesive.keplin.kotlin.core.scripting.ReplRepeatingMode
import uy.kohesive.keplin.kotlin.core.scripting.ResettableRepl
import javax.script.Compilable
import javax.script.Invocable
import javax.script.ScriptEngineFactory

open class CompilableJsr223ReplEngine(factory: ScriptEngineFactory,
                                      defaultImports: List<String> = emptyList())
    : AbstractCompilableScriptEngine(factory, defaultImports), Compilable, Invocable {

    override val engine: ResettableRepl by lazy {
        ResettableRepl(
                moduleName = moduleName,
                additionalClasspath = extraClasspath,
                repeatingMode = ReplRepeatingMode.REPEAT_ANY_PREVIOUS,
                scriptDefinition = scriptDefinition,
                sharedHostClassLoader = Thread.currentThread().contextClassLoader
        )
    }

    companion object {
        val jsr223EngineName = "keplin-kotin-repl-compilable"
    }
}
