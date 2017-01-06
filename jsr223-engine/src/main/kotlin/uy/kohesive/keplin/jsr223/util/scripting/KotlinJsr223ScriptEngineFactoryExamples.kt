package uy.kohesive.keplin.jsr223.util.scripting


import com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.cli.common.repl.KotlinJsr223JvmScriptEngineFactoryBase
import javax.script.ScriptContext
import javax.script.ScriptEngine

class KotlinJsr223JvmLocalScriptEngineFactory : KotlinJsr223JvmScriptEngineFactoryBase() {

    override fun getScriptEngine(): ScriptEngine =
            KotlinJsr223JvmLocalScriptEngine(
                    Disposer.newDisposable(),
                    this,
                    scriptCompilationClasspathFromContext(),
                    "kotlin.script.templates.standard.ScriptTemplateWithBindings",
                    { ctx -> arrayOf(ctx.getBindings(ScriptContext.ENGINE_SCOPE)) },
                    arrayOf(Map::class)
            )
}

private fun makeSerializableArgumentsForTemplateWithBindings(ctx: ScriptContext): Array<Any?> {
    val bindings = ctx.getBindings(ScriptContext.ENGINE_SCOPE)
    val serializableBindings = linkedMapOf<String, Any>()
    // TODO: consider deeper analysis and copying to serializable data if possible
    serializableBindings.putAll(bindings)
    return arrayOf(serializableBindings)
}
