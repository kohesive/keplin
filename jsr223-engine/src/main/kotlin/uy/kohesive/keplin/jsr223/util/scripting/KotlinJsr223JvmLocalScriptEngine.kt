package uy.kohesive.keplin.jsr223.util.scripting

import com.intellij.openapi.Disposable
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.utils.PathUtil
import uy.kohesive.keplin.jsr223.core.scripting.KotlinJsr223JvmInvocableScriptEngine
import uy.kohesive.keplin.jsr223.core.scripting.KotlinJsr223JvmScriptEngineBase
import uy.kohesive.keplin.kotlin.core.scripting.*
import java.io.File
import java.net.URLClassLoader
import javax.script.ScriptContext
import javax.script.ScriptEngineFactory
import kotlin.reflect.KClass

class KotlinJsr223JvmLocalScriptEngine(
        disposable: Disposable,
        factory: ScriptEngineFactory,
        val templateClasspath: List<File>,
        templateClassName: String,
        val getScriptArgs: (ScriptContext) -> Array<Any?>?,
        val scriptArgsTypes: Array<out KClass<out Any>>?
) : KotlinJsr223JvmScriptEngineBase(factory), KotlinJsr223JvmInvocableScriptEngine {

    override val replCompiler: ResettableReplCompiler by lazy {
        DefaultResettableReplCompiler(
                disposable,
                makeScriptDefinition(templateClasspath, templateClassName),
                makeCompilerConfiguration(),
                PrintingMessageCollector(System.out, MessageRenderer.WITHOUT_PATHS, false))
    }
    // TODO: bindings passing works only once on the first eval, subsequent setContext/setBindings call have no effect. Consider making it dynamic, but take history into account
    val localEvaluator by lazy {
        DefaultResettableReplEvaluator(templateClasspath,
                Thread.currentThread().contextClassLoader)
    }

    override val replEvaluator: ResettableReplEvaluator get() = localEvaluator
    override val replScriptEvaluator: ResettableReplEvaluatorBase get() = localEvaluator

    private fun makeScriptDefinition(templateClasspath: List<File>, templateClassName: String): KotlinScriptDefinitionEx {
        val classloader = URLClassLoader(templateClasspath.map { it.toURI().toURL() }.toTypedArray(), this.javaClass.classLoader)
        val cls = classloader.loadClass(templateClassName)
        return KotlinScriptDefinitionEx(cls.kotlin, ScriptArgsWithTypes(getScriptArgs(getContext()), scriptArgsTypes), emptyList())
    }

    private fun makeCompilerConfiguration() = CompilerConfiguration().apply {
        addJvmClasspathRoots(PathUtil.getJdkClassesRoots())
        addJvmClasspathRoots(templateClasspath)
        put(CommonConfigurationKeys.MODULE_NAME, "kotlin-script")
    }
}
