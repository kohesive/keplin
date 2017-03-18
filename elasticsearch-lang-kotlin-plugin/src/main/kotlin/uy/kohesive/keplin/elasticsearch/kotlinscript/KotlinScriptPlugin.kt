package uy.kohesive.keplin.elasticsearch.kotlinscript

import org.elasticsearch.common.settings.Setting
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.plugins.Plugin
import org.elasticsearch.plugins.ScriptPlugin
import org.elasticsearch.script.NativeScriptFactory
import org.elasticsearch.script.ScriptContext
import org.elasticsearch.script.ScriptEngineService

class KotlinScriptPlugin : Plugin(), ScriptPlugin {
    companion object {
        val LANGUAGE_NAME = "kotlin"
    }

    override fun getSettings(): List<Setting<*>> {
        return emptyList() // listOf(Setting.simpleString(KotlinPath, Setting.Property.NodeScope))
    }

    override fun getScriptEngineService(settings: Settings): ScriptEngineService {
        return KotlinScriptEngineService(settings)
    }

    override fun getNativeScripts(): List<NativeScriptFactory> = emptyList()

    override fun getCustomScriptContexts(): ScriptContext.Plugin? {
        return null
    }
}