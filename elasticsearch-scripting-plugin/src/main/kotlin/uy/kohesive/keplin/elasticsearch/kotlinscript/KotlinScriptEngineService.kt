package uy.kohesive.keplin.elasticsearch.kotlinscript

import org.apache.lucene.index.LeafReaderContext
import org.apache.lucene.search.Scorer
import org.elasticsearch.SpecialPermission
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.script.*
import org.elasticsearch.search.lookup.LeafSearchLookup
import org.elasticsearch.search.lookup.SearchLookup
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.repl.ReplCodeLine
import org.jetbrains.kotlin.cli.common.repl.ReplCompileResult
import org.jetbrains.kotlin.cli.common.repl.ScriptArgsWithTypes
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.cli.jvm.repl.GenericReplCompiler
import org.jetbrains.kotlin.com.intellij.openapi.project.Project
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.script.KotlinScriptDefinition
import org.jetbrains.kotlin.script.KotlinScriptExternalDependencies
import org.jetbrains.kotlin.utils.PathUtil
import uy.kohesive.keplin.util.ClassPathUtils.findRequiredScriptingJarFiles
import java.io.File
import java.security.AccessController
import java.security.PrivilegedAction
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KClass


interface ScriptTemplateEmptyArgsProvider {
    val defaultEmptyArgs: ScriptArgsWithTypes?
}

open class KotlinScriptDefinitionEx(template: KClass<out Any>,
                                    override val defaultEmptyArgs: ScriptArgsWithTypes?,
                                    val defaultImports: List<String> = emptyList())
    : KotlinScriptDefinition(template), ScriptTemplateEmptyArgsProvider {
    class EmptyDependencies() : KotlinScriptExternalDependencies
    class DefaultImports(val defaultImports: List<String>, val base: KotlinScriptExternalDependencies) : KotlinScriptExternalDependencies by base {
        override val imports: List<String> get() = (defaultImports + base.imports).distinct()
    }

    override fun <TF : Any> getDependenciesFor(file: TF, project: Project, previousDependencies: KotlinScriptExternalDependencies?): KotlinScriptExternalDependencies? {
        val base = super.getDependenciesFor(file, project, previousDependencies)
        return if (previousDependencies == null && defaultImports.isNotEmpty()) DefaultImports(defaultImports, base ?: EmptyDependencies())
        else base
    }
}

class KotlinScriptEngineService(val settings: Settings) : ScriptEngineService {
    companion object {
        val LANGUAGE_NAME = KotlinScriptPlugin.LANGUAGE_NAME
        val uniqueScriptId: AtomicInteger = AtomicInteger(0)

        val SCRIPT_RESULT_FIELD_NAME = "\$\$result"
    }

    val sm = System.getSecurityManager()

    val uniqueSessionId = UUID.randomUUID().toString()

    val tempDir = createTempDir("keplin-es-kotlinscript", uniqueSessionId)
    val clientAliveFile: File = File.createTempFile("keplin-es-kotlinscript", uniqueSessionId)
    val compilerMessages: MessageCollector = CapturingMessageCollector()

    val kotlinInstallDirName = settings.get(KotlinScriptPlugin.KotlinPath) ?: throw IllegalStateException("Invalid/missing setting ${KotlinScriptPlugin.KotlinPath} which should point to Kotlin install directory")
    val kotlinInstallDir = File(kotlinInstallDirName).absoluteFile

    // TODO: improve this handling, test this all during plugin loading
//    val kotlinCompilerJar = File(kotlinInstallDir, "lib/kotlin-compiler.jar").takeIf { it.exists() } ?: throw IllegalStateException("Invalid/missing Kotlin compiler JAR: ${kotlinInstallDir}/lib/kotlin-compiler.jar")

    //  val compilerClasspath = listOf(kotlinCompilerJar) + ClassPathUtils.findClassJars(EsKotlinScriptTemplate::class)

    val disposable = Disposer.newDisposable()
    val repl by lazy {
        sm.checkPermission(SpecialPermission())
        AccessController.doPrivileged(PrivilegedAction {
            val scriptDefinition = KotlinScriptDefinitionEx(EsKotlinScriptTemplate::class, makeArgs())
            val additionalClasspath = emptyList<File>()
            val moduleName = "kotlin-script-module-${uniqueSessionId}"
            val messageCollector = compilerMessages
            val compilerConfig = CompilerConfiguration().apply {
                addJvmClasspathRoots(PathUtil.getJdkClassesRoots())
                addJvmClasspathRoots(findRequiredScriptingJarFiles(scriptDefinition.template,
                        includeScriptEngine = false,
                        includeKotlinCompiler = false,
                        includeStdLib = true,
                        includeRuntime = true))
                addJvmClasspathRoots(additionalClasspath)
                put(CommonConfigurationKeys.MODULE_NAME, moduleName)
                put<MessageCollector>(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, messageCollector)
            }

            GenericReplCompiler(disposable, scriptDefinition, compilerConfig, messageCollector)
        })
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
            val executable = compiledScript.compiled() as PreparedScript
            return executable.code.invoker(executable.code, args)
        }

        override fun setNextVar(name: String, value: Any) {
            _mutableVars.put(name, value)
        }

    }

    override fun search(compiledScript: CompiledScript, lookup: SearchLookup, vars: Map<String, Any>?): SearchScript {
        return object : SearchScript {
            override fun needsScores(): Boolean {
                return (compiledScript.compiled() as PreparedScript).scoreFieldAccessed
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
            val executable = compiledScript.compiled() as PreparedScript
            return executable.code.invoker(executable.code, args)
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

    val scriptTemplateConstructor = ::ConcreteEsKotlinScriptTemplate

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
                        try {
                            val lambda = ClassSerDesUtil.deserLambdaInstanceSafely(className, serInstance, goodClassNames)
                            val scriptTemplate = scriptTemplateConstructor.call(*scriptArgs.scriptArgs)
                            lambda.invoke(scriptTemplate)
                        } catch (ex: Exception) {
                            throw ScriptException(ex.message ?: "Error executing Lambda", ex, emptyList(), scriptSource, LANGUAGE_NAME)
                        }
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
                sm.checkPermission(SpecialPermission())
                AccessController.doPrivileged(PrivilegedAction {

                    val codeLine = ReplCodeLine(scriptId, 0, scriptSource)
                    try {
                        val replState = repl.createState()
                        val replResult = repl.compile(replState, codeLine)
                        val compiledCode = when (replResult) {
                            is ReplCompileResult.Error -> throw toScriptException(replResult.message, scriptSource, replResult.location)
                            is ReplCompileResult.Incomplete -> throw toScriptException("Incomplete code", scriptSource, CompilerMessageLocation.NO_LOCATION)
                            is ReplCompileResult.CompiledClasses -> replResult
                        }

                        val classesAsBytes = compiledCode.classes.map {
                            NamedClassBytes(it.path.removeSuffix(".class").replace('/', '.'), it.bytes)
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
                            val completedScript = scriptConstructor.newInstance(*scriptArgs.scriptArgs)
                            resultField.get(completedScript)
                        }
                    } catch (ex: Exception) {
                        throw ScriptException(ex.message ?: "unknown error", ex, emptyList<String>(), scriptSource, LANGUAGE_NAME)
                    }

                })
            } catch (ex: Exception) {
                if (ex is ScriptException) throw ex
                else throw ScriptException(ex.message ?: "unknown error", ex, emptyList(), scriptSource, LANGUAGE_NAME)
            }
        }

        val verification = ClassRestrictionVerifier.verifySafeClass(executableCode.className, emptySet(), executableCode.classes)
        if (verification.failed) {
            val violations = verification.violations.sorted()
            val exp = Exception("Illegal Access to unauthorized classes/methods: ${violations.joinToString()}")
            throw  ScriptException(exp.message, exp, violations, scriptSource, LANGUAGE_NAME)
        }
        return PreparedScript(executableCode, verification.isScoreAccessed)
    }

    data class ExecutableCode(val className: String, val code: String, val classes: List<NamedClassBytes>, val extraData: Any? = null, val invoker: ExecutableCode.(ScriptArgsWithTypes) -> Any?)

    data class PreparedScript(val code: ExecutableCode, val scoreFieldAccessed: Boolean)

    data class NamedClassBytes(val className: String, val bytes: ByteArray)
}

fun toScriptException(message: String, code: String, location: CompilerMessageLocation): ScriptException {
    val msgs = message.split('\n')
    val text = msgs[0]
    val snippet = if (msgs.size > 1) msgs[1] else ""
    val errorMarker = if (msgs.size > 2) msgs[2] else ""
    val exp = Exception(text)
    return ScriptException(exp.message, exp, listOf(snippet, errorMarker), code, KotlinScriptEngineService.LANGUAGE_NAME)
}