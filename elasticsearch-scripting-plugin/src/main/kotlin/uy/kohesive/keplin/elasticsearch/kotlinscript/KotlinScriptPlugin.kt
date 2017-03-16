package uy.kohesive.keplin.elasticsearch.kotlinscript

import org.elasticsearch.common.settings.Settings
import org.elasticsearch.plugins.Plugin
import org.elasticsearch.plugins.ScriptPlugin
import org.elasticsearch.script.NativeScriptFactory
import org.elasticsearch.script.ScriptContext
import org.elasticsearch.script.ScriptEngineService

class KotlinScriptPlugin : Plugin(), ScriptPlugin {
    companion object {
        val prefUseDaemonCompiler = "kohesive.kotlinscript.compiler.useDaemon"
        val defaultForPrefUseDaemonCompiler = true
    }

    override fun getScriptEngineService(settings: Settings): ScriptEngineService {
        return KotlinScriptEngineService(settings)
    }

    override fun getNativeScripts(): List<NativeScriptFactory> = emptyList()

    override fun getCustomScriptContexts(): ScriptContext.Plugin? {
        return null
    }
}