package uy.kohesive.keplin.elasticsearch.kotlinscript

import org.elasticsearch.common.settings.Settings
import org.elasticsearch.script.ExecutableScript
import org.elasticsearch.script.ScriptEngineService
import org.elasticsearch.script.ScriptException
import org.elasticsearch.script.SearchScript
import org.elasticsearch.search.lookup.SearchLookup
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.repl.ReplCodeLine
import org.jetbrains.kotlin.cli.common.repl.ReplCompileResult
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.daemon.client.*
import org.jetbrains.kotlin.daemon.common.*
import uy.kohesive.keplin.util.ClassPathUtils
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.rmi.RemoteException
import java.util.*
import java.util.concurrent.atomic.AtomicInteger


class KotlinScriptEngineService(val settings: Settings) : ScriptEngineService {
    companion object {
        val LANGUAGE_NAME = "kotlin"
        val uniqueScriptId: AtomicInteger = AtomicInteger(0)
    }

    val useDaemonCompiler = settings.getAsBoolean(KotlinScriptPlugin.prefUseDaemonCompiler, KotlinScriptPlugin.defaultForPrefUseDaemonCompiler)
    val uniqueSessionId = UUID.randomUUID().toString()

    val compiler by lazy { if (useDaemonCompiler) connectToCompileService() else makeLocalCompilerService() }

    val disposable = Disposer.newDisposable()

    override fun getExtension(): String = LANGUAGE_NAME

    override fun getType(): String = LANGUAGE_NAME

    override fun executable(compiledScript: org.elasticsearch.script.CompiledScript, vars: Map<String, Any>?): ExecutableScript {
        TODO()
    }

    override fun search(compiledScript: org.elasticsearch.script.CompiledScript, lookup: SearchLookup, vars: Map<String, Any>?): SearchScript {
        TODO()
    }

    override fun close() {
        disposable.dispose()
    }

    override fun compile(scriptName: String?, scriptSource: String, params: Map<String, String>?): Any {
        val executableCode = if (ClassSerDesUtil.isPrefixedBase64(scriptSource)) {
            try {
                val (className, classesAsBytes, serInstance) = ClassSerDesUtil.deserFromPrefixedBase64(scriptSource)
                ExecutableCode(className, scriptSource, classesAsBytes, serInstance) {
                    // TODO: invoke code
                }
            } catch (ex: Exception) {
                if (ex is ScriptException) throw ex
                else throw ScriptException(ex.message ?: "unknown error", ex, emptyList(), scriptSource, LANGUAGE_NAME)
            }
        } else {
            val compilerOutCapture = ByteArrayOutputStream()
            try {
                val sessionId: Int? = if (useDaemonCompiler) leaseSession(compiler, compilerOutCapture) else null
                try {
                    val codeLine = ReplCodeLine(uniqueScriptId.incrementAndGet(), scriptSource)
                    try {
                        val daemonResult = compiler.remoteReplLineCompile(sessionId!!, codeLine, emptyList())
                        if (!daemonResult.isGood) {
                            throw ScriptException("Unknown daemon compiling failure", null, emptyList<String>(), scriptSource, LANGUAGE_NAME)
                        }
                        val replResult = daemonResult.get()
                        val compiledCode = when (replResult) {
                            is ReplCompileResult.Error -> throw toScriptException(replResult.message, scriptSource, replResult.location)
                            is ReplCompileResult.Incomplete -> throw toScriptException("Incomplete code", scriptSource, CompilerMessageLocation.NO_LOCATION)
                            is ReplCompileResult.HistoryMismatch -> throw toScriptException("History mismatch", scriptSource, CompilerMessageLocation.NO_LOCATION)
                            is ReplCompileResult.CompiledClasses -> replResult
                        }

                        val newClasses = compiledCode.classes.map { it.bytes }

                        ExecutableCode(compiledCode.generatedClassname, scriptSource, newClasses) {
                            // TODO: invoke code
                        }
                    } catch (ex: Exception) {
                        throw ScriptException(ex.message ?: "unknown error", ex, emptyList<String>(), scriptSource, LANGUAGE_NAME)
                    }
                } finally {
                    if (sessionId != null) {
                        try {
                            compiler.releaseReplSession(sessionId)
                        } catch (ex: RemoteException) {
                            // assuming that communication failed and daemon most likely is already down
                        }
                    }
                }
            } catch (ex: Exception) {
                if (ex is ScriptException) throw ex
                else throw ScriptException(ex.message ?: "unknown error", ex, emptyList(), scriptSource, LANGUAGE_NAME)
            }
        }

        val verification = ClassRestrictionVerifier.verifySafeClass(executableCode.className, emptySet(), executableCode.classes)
        if (verification.failed) {
            throw  ScriptException("Illegal Access to unauthorized classes/methods", null, verification.violations.sorted(), scriptSource, LANGUAGE_NAME)
        }
        return PreparedScript(executableCode, verification.isScoreAccessed)
    }

    fun toScriptException(message: String, code: String, location: CompilerMessageLocation): ScriptException {
        val msgs = message.split('\n')
        val text = msgs[0]
        val snippet = if (msgs.size > 1) msgs[1] else ""
        val errorMarker = if (msgs.size > 2) msgs[2] else ""
        return ScriptException(text, null, listOf(snippet, errorMarker), code, LANGUAGE_NAME)
    }

    private fun connectToCompileService(compilerJar: File = ClassPathUtils.kotlinCompilerJar): CompileService {
        val compilerId = CompilerId.makeCompilerId(compilerJar)
        val daemonOptions = configureDaemonOptions()
        val daemonJVMOptions = DaemonJVMOptions()

        val daemonReportMessages = arrayListOf<DaemonReportMessage>()

        return KotlinCompilerClient.connectToCompileService(compilerId, daemonJVMOptions, daemonOptions, DaemonReportingTargets(null, daemonReportMessages), true, true)
                ?: throw IllegalStateException("Unable to connect to repl server:" + daemonReportMessages.joinToString("\n  ", prefix = "\n  ") { "${it.category.name} ${it.message}" })
    }

    private fun leaseSession(compiler: CompileService, compilerOut: OutputStream): Int {
        val sessionId = compiler.leaseReplSession(
                makeAutodeletingFlagFile("keplin-ES-kotlinscript-${uniqueSessionId}").absolutePath,
                CompileService.TargetPlatform.JVM,
                CompilerCallbackServicesFacadeServer(port = SOCKET_ANY_FREE_PORT),
                ClassPathUtils.findClassJars(EsKotlinScriptTemplate::class),
                EsKotlinScriptTemplate::class.java.canonicalName,
                EMPTY_SCRIPT_ARGS,
                SCRIPT_ARGS_TYPES.map { it.java }.toTypedArray(),
                RemoteOutputStreamServer(compilerOut, SOCKET_ANY_FREE_PORT),
                null, null, null, null).get()
        return sessionId
    }

    private fun makeLocalCompilerService(): CompileService {
        TODO()
    }

    data class ExecutableCode(val className: String, val code: String, val classes: List<ByteArray>, val extraData: Any? = null, val invoker: () -> Any?)

    data class PreparedScript(val code: ExecutableCode, val scoreFieldAccessed: Boolean)
}