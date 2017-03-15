package uy.kohesive.keplin.kotlin.script.jsr223

import org.jetbrains.kotlin.cli.common.repl.*
import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.daemon.client.DaemonReportMessage
import org.jetbrains.kotlin.daemon.client.DaemonReportingTargets
import org.jetbrains.kotlin.daemon.client.KotlinCompilerClient
import org.jetbrains.kotlin.daemon.client.KotlinRemoteReplCompiler
import org.jetbrains.kotlin.daemon.common.*
import java.io.File
import java.io.OutputStream
import javax.script.ScriptContext
import javax.script.ScriptEngineFactory
import javax.script.ScriptException
import kotlin.reflect.KClass

class BasicKotlinJsr223DaemonCompileScriptEngine(
        disposable: Disposable,
        factory: ScriptEngineFactory,
        compilerJar: File,
        templateClasspath: List<File>,
        templateClassName: String,
        getScriptArgs: (ScriptContext, Array<out KClass<out Any>>?) -> ScriptArgsWithTypes?,
        scriptArgsTypes: Array<out KClass<out Any>>?,
        compilerOut: OutputStream = System.err
) : KotlinJsr223JvmScriptEngineBase(factory), KotlinJsr223JvmInvocableScriptEngine {

    private val daemon by lazy { connectToCompileService(compilerJar) }

    override val replCompiler by lazy {
        daemon.let {
            KotlinRemoteReplCompiler(
                    daemon,
                    makeAutodeletingFlagFile("jsr223-repl-session"),
                    CompileService.TargetPlatform.JVM,
                    templateClasspath,
                    templateClassName,
                    compilerOut)
        }
    }

    // TODO: bindings passing works only once on the first eval, subsequent setContext/setBindings call have no effect. Consider making it dynamic, but take history into account
    val localEvaluator by lazy { GenericReplCompilingEvaluator(replCompiler, templateClasspath, Thread.currentThread().contextClassLoader, getScriptArgs(getContext(), scriptArgsTypes)) }

    override val replScriptEvaluator: ReplFullEvaluator get() = localEvaluator

    private fun connectToCompileService(compilerJar: File): CompileService {
        val compilerId = CompilerId.makeCompilerId(compilerJar)
        val daemonOptions = configureDaemonOptions()
        val daemonJVMOptions = DaemonJVMOptions()

        val daemonReportMessages = arrayListOf<DaemonReportMessage>()

        return KotlinCompilerClient.connectToCompileService(compilerId, daemonJVMOptions, daemonOptions, DaemonReportingTargets(null, daemonReportMessages), true, true)
                ?: throw ScriptException("Unable to connect to repl server:" + daemonReportMessages.joinToString("\n  ", prefix = "\n  ") { "${it.category.name} ${it.message}" })
    }
}