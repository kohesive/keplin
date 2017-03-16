@file:Suppress("unused")

// could be used externally in javax.script.ScriptEngineFactory META-INF file

package uy.kohesive.keplin.kotlin.script.jsr223

import org.jetbrains.kotlin.cli.common.repl.KotlinJsr223JvmScriptEngineFactoryBase
import org.jetbrains.kotlin.cli.common.repl.ScriptArgsWithTypes
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import uy.kohesive.keplin.util.ClassPathUtils
import javax.script.ScriptContext
import javax.script.ScriptEngine

class BasicKotlinJsr223LocalScriptEngineFactory : KotlinJsr223JvmScriptEngineFactoryBase() {

    override fun getScriptEngine(): ScriptEngine =
            BasicKotlinJsr223LocalScriptEngine(
                    Disposer.newDisposable(),
                    this,
                    ClassPathUtils.scriptCompilationClasspathFromContext(),
                    "kotlin.script.templates.standard.ScriptTemplateWithBindings",
                    { ctx, types -> ScriptArgsWithTypes(arrayOf(ctx.getBindings(ScriptContext.ENGINE_SCOPE)), types ?: emptyArray()) },
                    arrayOf(Map::class)
            )
}

class BasicKotlinJsr223DaemonCompileScriptEngineFactory : KotlinJsr223JvmScriptEngineFactoryBase() {

    override fun getScriptEngine(): ScriptEngine =
            BasicKotlinJsr223DaemonCompileScriptEngine(
                    Disposer.newDisposable(),
                    this,
                    ClassPathUtils.kotlinCompilerJar,
                    ClassPathUtils.scriptCompilationClasspathFromContext(),
                    "kotlin.script.templates.standard.ScriptTemplateWithBindings",
                    { ctx, types -> ScriptArgsWithTypes(arrayOf(ctx.getBindings(ScriptContext.ENGINE_SCOPE)), types ?: emptyArray()) },
                    arrayOf(Map::class)
            )
}



