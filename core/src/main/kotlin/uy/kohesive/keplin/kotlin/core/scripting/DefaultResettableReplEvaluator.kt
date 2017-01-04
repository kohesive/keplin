package uy.kohesive.keplin.kotlin.core.scripting


import org.jetbrains.kotlin.cli.common.repl.InvokeWrapper
import org.jetbrains.kotlin.cli.common.repl.ReplClassLoader
import org.jetbrains.kotlin.cli.common.repl.ReplCodeLine
import org.jetbrains.kotlin.cli.common.repl.renderReplStackTrace
import org.jetbrains.kotlin.resolve.jvm.JvmClassName
import java.io.File
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

open class DefaultResettableReplEvaluator(baseClasspath: Iterable<File>,
                                          baseClassloader: ClassLoader?,
                                          val stateLock: ReentrantReadWriteLock = ReentrantReadWriteLock()) : ResettableReplEvaluator {

    private val topClassLoader: ReplClassLoader = makeReplClassLoader(baseClassloader, baseClasspath)

    private val history = ResettableReplHistory<EvalClassWithInstanceAndLoader>()

    override fun resetToLine(lineNumber: Int): List<ReplCodeLine> {
        return stateLock.write {
            history.resetToLine(lineNumber)
        }.map { it.first }
    }

    override val evaluationHistory: List<ReplCodeLine> get() = stateLock.read { history.historyAsSource() }

    override fun eval(compileResult: ResettableReplCompiler.Response.CompiledClasses,
                      invokeWrapper: InvokeWrapper?,
                      verifyHistory: List<ReplCodeLine>,
                      scriptArgs: ScriptArgsWithTypes?): ResettableReplEvaluator.Response {
        stateLock.write {
            val firstMismatch = history.firstMismatchingHistory(verifyHistory)
            if (firstMismatch != null) {
                return@eval ResettableReplEvaluator.Response.HistoryMismatch(history.historyAsSource(), firstMismatch)
            }

            val historyCopy = history.copyValues()
            var mainLineClassName: String? = null
            val classLoader = makeReplClassLoader(history.lastValue()?.classLoader ?: topClassLoader, compileResult.classpathAddendum)

            fun classNameFromPath(path: String) = JvmClassName.byInternalName(path.replaceFirst("\\.class$".toRegex(), ""))

            fun processCompiledClasses() {
                // TODO: get class name from compiledResult instead of line number
                val expectedClassName = compileResult.generatedClassname
                compileResult.classes.filter { it.path.endsWith(".class") }
                        .forEach {
                            val className = classNameFromPath(it.path)
                            if (className.internalName == expectedClassName || className.internalName.endsWith("/$expectedClassName")) {
                                mainLineClassName = className.internalName.replace('/', '.')
                            }
                            classLoader.addClass(className, it.bytes)
                        }
            }

            fun compiledClassesNames() = compileResult.classes.map { classNameFromPath(it.path).fqNameForClassNameWithoutDollars.asString() }

            processCompiledClasses()

            val scriptClass = try {
                classLoader.loadClass(mainLineClassName!!)
            } catch (e: Throwable) {
                return@eval ResettableReplEvaluator.Response.Error.Runtime(history.historyAsSource(),
                        "Error loading class $mainLineClassName: known classes: ${compiledClassesNames()}",
                        e as? Exception)
            }

            val useScriptArgs = scriptArgs?.scriptArgs
            val useScriptArgsTypes = scriptArgs?.scriptArgsTypes?.map { it.java }

            val constructorParams: Array<Class<*>> = (historyCopy.map { it.klass.java } +
                    (useScriptArgs?.mapIndexed { i, it -> useScriptArgsTypes?.getOrNull(i) ?: it?.javaClass ?: Any::class.java } ?: emptyList())
                    ).toTypedArray()
            val constructorArgs: Array<Any?> = (historyCopy.map { it.instance } + useScriptArgs.orEmpty()).toTypedArray()

            val scriptInstanceConstructor = scriptClass.getConstructor(*constructorParams)

            // add place holder in case of recursion
            history.add(compileResult.compiledCodeLine, EvalClassWithInstanceAndLoader(scriptClass.kotlin, null, classLoader))

            val scriptInstance =
                    try {
                        if (invokeWrapper != null) invokeWrapper.invoke { scriptInstanceConstructor.newInstance(*constructorArgs) }
                        else scriptInstanceConstructor.newInstance(*constructorArgs)
                    } catch (e: Throwable) {
                        // drop the placeholder history item
                        history.removeLast(compileResult.compiledCodeLine)

                        // ignore everything in the stack trace until this constructor call
                        return@eval ResettableReplEvaluator.Response.Error.Runtime(history.historyAsSource(),
                                renderReplStackTrace(e.cause!!, startFromMethodName = "${scriptClass.name}.<init>"), e as? Exception)
                    }

            // update placeholder with instance
            history.removeLast(compileResult.compiledCodeLine)
            history.add(compileResult.compiledCodeLine, EvalClassWithInstanceAndLoader(scriptClass.kotlin, scriptInstance, classLoader))

            val resultField = scriptClass.getDeclaredField(SCRIPT_RESULT_FIELD_NAME).apply { isAccessible = true }
            val resultValue: Any? = resultField.get(scriptInstance)

            return if (compileResult.hasResult) ResettableReplEvaluator.Response.ValueResult(history.historyAsSource(), resultValue)
            else ResettableReplEvaluator.Response.UnitResult(history.historyAsSource())
        }
    }

    override val lastEvaluatedScript: EvalClassWithInstanceAndLoader? get() {
        return stateLock.read { history.lastValue() }
    }

    companion object {
        private val SCRIPT_RESULT_FIELD_NAME = "\$\$result"
    }
}
