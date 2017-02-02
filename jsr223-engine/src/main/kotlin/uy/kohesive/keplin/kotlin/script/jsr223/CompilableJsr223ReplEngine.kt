package uy.kohesive.keplin.kotlin.script.jsr223

import org.jetbrains.kotlin.cli.common.repl.ReplRepeatingMode
import uy.kohesive.keplin.kotlin.script.SimplifiedRepl
import uy.kohesive.keplin.kotlin.script.jsr223.core.AbstractCompilableScriptEngine
import javax.script.Compilable
import javax.script.Invocable
import javax.script.ScriptEngineFactory

open class CompilableJsr223ReplEngine(factory: ScriptEngineFactory,
                                      defaultImports: List<String> = emptyList())
    : AbstractCompilableScriptEngine(factory, defaultImports), Compilable, Invocable {

    override val engine: SimplifiedRepl by lazy {
        SimplifiedRepl(
                moduleName = moduleName,
                additionalClasspath = extraClasspath,
                repeatingMode = ReplRepeatingMode.REPEAT_ANY_PREVIOUS,
                scriptDefinition = scriptDefinition,
                sharedHostClassLoader = Thread.currentThread().contextClassLoader
        )
    }

    companion object {
        val jsr223EngineName = "kotin-repl-compilable"
    }
}

