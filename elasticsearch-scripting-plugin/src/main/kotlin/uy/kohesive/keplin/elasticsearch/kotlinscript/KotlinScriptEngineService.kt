package uy.kohesive.keplin.elasticsearch.kotlinscript

import org.apache.lucene.index.LeafReaderContext
import org.apache.lucene.search.Scorer
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.script.*
import org.elasticsearch.search.lookup.LeafSearchLookup
import org.elasticsearch.search.lookup.SearchLookup
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.repl.ReplCodeLine
import org.jetbrains.kotlin.cli.common.repl.ReplCompileResult
import org.jetbrains.kotlin.cli.common.repl.ScriptArgsWithTypes
import org.jetbrains.kotlin.daemon.client.*
import org.jetbrains.kotlin.daemon.common.*
import org.jetbrains.kotlin.daemon.common.CompileService
import uy.kohesive.keplin.util.ClassPathUtils
import java.io.File
import java.rmi.RemoteException
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.full.primaryConstructor


class KotlinScriptEngineService(val settings: Settings) : ScriptEngineService {
    companion object {
        val LANGUAGE_NAME = KotlinScriptPlugin.LANGUAGE_NAME
        val uniqueScriptId: AtomicInteger = AtomicInteger(0)

        private val SCRIPT_RESULT_FIELD_NAME = "\$\$result"
    }

    val uniqueSessionId = UUID.randomUUID().toString()

    val tempDir = createTempDir("keplin-es-kotlinscript", uniqueSessionId)
    val clientAliveFile: File = File.createTempFile("keplin-es-kotlinscript", uniqueSessionId)
    val compilerMessages: MessageCollector = CapturingMessageCollector()

    val kotlinInstallDirName = settings.get(KotlinScriptPlugin.KotlinPath) ?: throw IllegalStateException("Invalid/missing setting ${KotlinScriptPlugin.KotlinPath} which should point to Kotlin install directory")
    val kotlinInstallDir = File(kotlinInstallDirName).absoluteFile

    // TODO: improve this handling, test this all during plugin loading
    val kotlinCompilerJar = File(kotlinInstallDir, "lib/kotlin-compiler.jar").takeIf { it.exists() } ?: throw IllegalStateException("Invalid/missing Kotlin compiler JAR: ${kotlinInstallDir}/lib/kotlin-compiler.jar")

    val compilerClasspath = listOf(kotlinCompilerJar) + ClassPathUtils.findClassJars(EsKotlinScriptTemplate::class)

    val compilerService by lazy {
        val compilerId = CompilerId.makeCompilerId(compilerClasspath)
        val daemonOptions = DaemonOptions(runFilesPath = File(tempDir, "daemonRunPath").absolutePath,
                verbose = true,
                reportPerf = true)
        val daemonJVMOptions = org.jetbrains.kotlin.daemon.common.DaemonJVMOptions()
        val daemonReportMessages = arrayListOf<DaemonReportMessage>()

        KotlinCompilerClient.connectToCompileService(compilerId, clientAliveFile, daemonJVMOptions, daemonOptions,
                DaemonReportingTargets(messages = daemonReportMessages, messageCollector = compilerMessages), true)
                ?: throw IllegalStateException("Unable to connect to repl server:" + daemonReportMessages.joinToString("\n  ", prefix = "\n  ") { "${it.category.name} ${it.message}" })
    }

    override fun getExtension(): String = LANGUAGE_NAME

    override fun getType(): String = LANGUAGE_NAME

    override fun executable(compiledScript: CompiledScript, vars: Map<String, Any>?): ExecutableScript {
        return ExecutableKotlin(compiledScript, vars)
    }

    class ExecutableKotlin(val compiledScript: CompiledScript, val vars: Map<String, Any>?) : ExecutableScript {
        val _mutableVars: MutableMap<String, Any> = HashMap<String, Any>(vars)

        override fun run(): Any? {
            val args = makeArgs(variables = _mutableVars.toWrapped())
            val executable = compiledScript.compiled() as ExecutableCode
            return executable.invoker(executable, args)
        }

        override fun setNextVar(name: String, value: Any) {
            _mutableVars.put(name, value)
        }

    }

    override fun search(compiledScript: CompiledScript, lookup: SearchLookup, vars: Map<String, Any>?): SearchScript {
        return object : SearchScript {
            override fun needsScores(): Boolean {
                return true // TODO: ASM scan to find if we reference the score getter
            }

            override fun getLeafSearchScript(context: LeafReaderContext?): LeafSearchScript {
                return LeafSearchScriptKotlin(compiledScript, vars, lookup.getLeafSearchLookup(context))
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    class LeafSearchScriptKotlin(val compiledScript: CompiledScript, val vars: Map<String, Any>?, val lookup: LeafSearchLookup) : LeafSearchScript {
        val _mutableVars: MutableMap<String, Any> = HashMap<String, Any>(vars).apply {
            putAll(lookup.asMap())
        }

        var _doc = lookup.doc() as MutableMap<String, List<Any>>
        var _aggregationValue: Any? = null
        var _scorer: Scorer? = null

        // TODO:  implement something for _score and ctx standard var names

        override fun run(): Any? {
            val score = _scorer?.score()?.toDouble() ?: 0.0
            val ctx = _mutableVars.get("ctx") as? Map<Any, Any> ?: emptyMap()
            val args = makeArgs(_mutableVars.toWrapped(), score, _doc, ctx, _aggregationValue)
            val executable = compiledScript.compiled() as ExecutableCode
            return executable.invoker(executable, args)
        }

        override fun setScorer(scorer: Scorer?) {
            _scorer = scorer
        }

        override fun setNextVar(name: String, value: Any) {
            _mutableVars.put(name, value)
        }

        override fun setNextAggregationValue(value: Any?) {
            _aggregationValue = value
        }

        override fun runAsDouble(): Double {
            return run() as Double
        }

        override fun runAsLong(): Long {
            return run() as Long
        }

        override fun setSource(source: MutableMap<String, Any>?) {
            lookup.source().setSource(source)
        }

        override fun setDocument(doc: Int) {
            lookup.setDocument(doc)
        }
    }

    override fun close() {

    }

    val scriptTemplateConstructor = EsKotlinScriptTemplate::class.primaryConstructor!!
    
    override fun compile(scriptName: String?, scriptSource: String, params: Map<String, String>?): Any {
        val executableCode = if (ClassSerDesUtil.isPrefixedBase64(scriptSource)) {
            try {
                val (className, classesAsBytes, serInstance) = ClassSerDesUtil.deserFromPrefixedBase64(scriptSource)
                val classLoader = ScriptClassLoader(Thread.currentThread().contextClassLoader).apply {
                    classesAsBytes.forEach {
                        addClass(it.className, it.bytes)
                    }
                }
                val goodClassNames = (classesAsBytes.map { it.className } + className).toSet()
                ExecutableCode(className, scriptSource, classesAsBytes, serInstance) { scriptArgs ->
                    val ocl = Thread.currentThread().contextClassLoader
                    try {
                        Thread.currentThread().contextClassLoader = classLoader
                        // deser every time in case it is mutable, we don't want a changing base (or is that really possible?)
                        val lambda = ClassSerDesUtil.deserLambdaInstanceSafely(className, serInstance, goodClassNames)
                        lambda.invoke(scriptTemplateConstructor.call(scriptArgs.scriptArgs))
                    } finally {
                        Thread.currentThread().contextClassLoader = ocl
                    }
                }
            } catch (ex: Exception) {
                if (ex is ScriptException) throw ex
                else throw ScriptException(ex.message ?: "unknown error", ex, emptyList(), scriptSource, LANGUAGE_NAME)
            }
        } else {
            val scriptId = uniqueScriptId.incrementAndGet()
            val compilerOutCapture = CapturingMessageCollector()
            val compilerOutputs = arrayListOf<File>()
            try {
                // val sessionId: Int = leaseSession(compiler, compilerOutCapture, compilerOutputs)
                val compilerArgs = arrayOf<String>()
                val compiler = KotlinRemoteReplCompilerClient(compilerService, clientAliveFile, CompileService.TargetPlatform.JVM, compilerArgs,
                        compilerMessages, ClassPathUtils.findClassJars(EsKotlinScriptTemplate::class),
                        EsKotlinScriptTemplate::class.java.canonicalName)
                try {
                    val codeLine = ReplCodeLine(scriptId, 0, scriptSource)
                    try {
                        val replState = compiler.createState()
                        val replResult = compiler.compile(replState, codeLine)
                        // val replState = compiler.replCreateState(sessionId).get().getId()
                        // val daemonResult = compiler.replCompile(sessionId, replState, codeLine)
                        // if (!daemonResult.isGood) {
                        //     throw ScriptException("Unknown daemon compiling failure", null, emptyList<String>(), scriptSource, LANGUAGE_NAME)
                        // }
                        // val replResult = daemonResult.get()
                        val compiledCode = when (replResult) {
                            is ReplCompileResult.Error -> throw toScriptException(replResult.message, scriptSource, replResult.location)
                            is ReplCompileResult.Incomplete -> throw toScriptException("Incomplete code", scriptSource, CompilerMessageLocation.NO_LOCATION)
                            is ReplCompileResult.CompiledClasses -> replResult
                        }

                        val classesAsBytes = compiledCode.classes.map {
                            NamedClassBytes(it.path, it.bytes)
                        }

                        val classLoader = ScriptClassLoader(Thread.currentThread().contextClassLoader).apply {
                            classesAsBytes.forEach {
                                addClass(it.className, it.bytes)
                            }
                        }

                        val goodClassNames = (classesAsBytes.map { it.className } + compiledCode.mainClassName).toSet()
                        val scriptClass = classLoader.loadClass(compiledCode.mainClassName)
                        val scriptConstructor = scriptClass.constructors.first()
                        val resultField = scriptClass.getDeclaredField(SCRIPT_RESULT_FIELD_NAME).apply { isAccessible = true }

                        ExecutableCode(compiledCode.mainClassName, scriptSource, classesAsBytes) { scriptArgs ->
                            val completedScript = scriptConstructor.newInstance(scriptArgs.scriptArgs)
                            resultField.get(completedScript)
                        }
                    } catch (ex: Exception) {
                        throw ScriptException(ex.message ?: "unknown error", ex, emptyList<String>(), scriptSource, LANGUAGE_NAME)
                    }
                } finally {
                    try {
                        compiler.dispose()
                        //compiler.releaseReplSession(sessionId)
                    } catch (ex: RemoteException) {
                        // assuming that communication failed and daemon most likely is already down
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

    private fun leaseSession(compiler: CompileService, messageCollector: MessageCollector, outputs: MutableList<File>): Int {
        val compilerArgs = arrayOf<String>()
        val compilerOptions = CompilationOptions(CompilerMode.NON_INCREMENTAL_COMPILER, CompileService.TargetPlatform.JVM,
                arrayOf(ReportCategory.COMPILER_MESSAGE.code, ReportCategory.EXCEPTION.code),
                ReportSeverity.ERROR.code, arrayOf())
        val sessionId = compiler.leaseReplSession(clientAliveFile.absolutePath,
                compilerArgs, compilerOptions,
                BasicCompilerServicesWithResultsFacadeServer(messageCollector, { outFile, sourceFiles ->
                    outputs.add(outFile)
                }),
                ClassPathUtils.findClassJars(EsKotlinScriptTemplate::class),
                EsKotlinScriptTemplate::class.java.canonicalName)
        return sessionId.get()
    }

    private fun makeLocalCompilerService(): CompileService {
        TODO()
    }

    data class ExecutableCode(val className: String, val code: String, val classes: List<NamedClassBytes>, val extraData: Any? = null, val invoker: ExecutableCode.(ScriptArgsWithTypes) -> Any?)

    data class PreparedScript(val code: ExecutableCode, val scoreFieldAccessed: Boolean)

    data class NamedClassBytes(val className: String, val bytes: ByteArray)

}