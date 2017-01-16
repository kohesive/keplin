package uy.kohesive.keplin.jsr223

import uy.kohesive.keplin.jsr223.core.scripting.AbstractInvocableReplScriptEngine
import uy.kohesive.keplin.kotlin.core.scripting.ReplRepeatingMode
import uy.kohesive.keplin.kotlin.core.scripting.ResettableRepl

open class EvalOnlyJsr223ReplEngine(factory: EvalOnlyReplEngineFactory,
                                    defaultImports: List<String> = emptyList())
    : AbstractInvocableReplScriptEngine(factory, defaultImports) {

    override val engine: ResettableRepl by lazy {
        ResettableRepl(
                moduleName = moduleName,
                additionalClasspath = extraClasspath,
                repeatingMode = ReplRepeatingMode.NONE,
                scriptDefinition = scriptDefinition,
                sharedHostClassLoader = Thread.currentThread().contextClassLoader
        )
    }

    companion object {
        val jsr223EngineName = "keplin-kotin"
    }
}


