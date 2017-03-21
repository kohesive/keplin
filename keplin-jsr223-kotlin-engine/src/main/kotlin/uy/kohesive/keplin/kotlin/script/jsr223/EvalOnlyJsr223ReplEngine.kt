package uy.kohesive.keplin.kotlin.script.jsr223

import org.jetbrains.kotlin.cli.common.repl.ReplRepeatingMode
import uy.kohesive.keplin.kotlin.script.SimplifiedRepl
import uy.kohesive.keplin.kotlin.script.jsr223.core.AbstractInvocableReplScriptEngine
import javax.script.ScriptEngineFactory

open class EvalOnlyJsr223ReplEngine(factory: ScriptEngineFactory,
                                    defaultImports: List<String> = emptyList())
    : AbstractInvocableReplScriptEngine(factory, defaultImports) {

    override val engine: SimplifiedRepl by lazy {
        SimplifiedRepl(
                moduleName = moduleName,
                additionalClasspath = extraClasspath,
                repeatingMode = ReplRepeatingMode.NONE,
                scriptDefinition = scriptDefinition,
                sharedHostClassLoader = Thread.currentThread().contextClassLoader
        )
    }

    companion object {
        val jsr223EngineName = "keplin-kotlin-repl-eval-only"
    }
}


