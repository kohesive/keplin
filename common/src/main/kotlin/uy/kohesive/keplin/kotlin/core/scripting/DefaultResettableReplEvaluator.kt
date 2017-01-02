package uy.kohesive.keplin.kotlin.core.scripting


import org.jetbrains.kotlin.cli.common.repl.InvokeWrapper
import org.jetbrains.kotlin.cli.common.repl.ReplClassLoader
import org.jetbrains.kotlin.cli.common.repl.ReplCodeLine
import org.jetbrains.kotlin.cli.common.repl.renderReplStackTrace
import org.jetbrains.kotlin.resolve.jvm.JvmClassName
import uy.kohesive.keplin.kotlin.core.scripting.ResettableReplHistory
import uy.kohesive.keplin.kotlin.core.scripting.makeReplClassLoader
import java.io.File
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

open class DefaultResettableReplEvaluator(baseClasspath: Iterable<File>,
                                          baseClassloader: ClassLoader?,
                                          val scriptArgs: Array<Any?>? = null,
                                          val scriptArgsTypes: Array<Class<*>>? = null) : ResettableReplEvaluator {

    private val topClassLoader: ReplClassLoader = makeReplClassLoader(baseClassloader, baseClasspath)

    // TODO: consider to expose it as a part of (evaluator, invoker) interface
    private val evalStateLock = ReentrantReadWriteLock()

    private val compiledLoadedClassesHistory = ResettableReplHistory<EvalClassWithInstanceAndLoader>()

    override fun resetToLine(lineNumber: Int): List<ReplCodeLine> {
        return compiledLoadedClassesHistory.resetToLine(lineNumber).map { it.first }
    }

    override val evaluationHistory: List<ReplCodeLine> get() = compiledLoadedClassesHistory.historyAsSource()

    override fun eval(compileResult: ResettableReplCompiler.Response.CompiledClasses,
                      invokeWrapper: InvokeWrapper?,
                      verifyHistory: List<ReplCodeLine>): ResettableReplEvaluator.Response = evalStateLock.write {

        val firstMismatch = compiledLoadedClassesHistory.firstMismatchingHistory(verifyHistory)
        if (firstMismatch != null) {
            return@eval ResettableReplEvaluator.Response.HistoryMismatch(compiledLoadedClassesHistory.historyAsSource(), firstMismatch)
        }

        val historyCopy = compiledLoadedClassesHistory.copyValues()

        var mainLineClassName: String? = null

        fun classNameFromPath(path: String) = JvmClassName.byInternalName(path.replaceFirst("\\.class$".toRegex(), ""))

        val classLoader = makeReplClassLoader(compiledLoadedClassesHistory.lastValue()?.classLoader ?: topClassLoader, compileResult.classpathAddendum)

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

        processCompiledClasses()

        fun compiledClassesNames() = compileResult.classes.map { classNameFromPath(it.path).fqNameForClassNameWithoutDollars.asString() }

        val scriptClass = try {
            classLoader.loadClass(mainLineClassName!!)
        } catch (e: Throwable) {
            return ResettableReplEvaluator.Response.Error.Runtime(compiledLoadedClassesHistory.historyAsSource(),
                    "Error loading class $mainLineClassName: known classes: ${compiledClassesNames()}",
                    e as? Exception)
        }

        fun getConstructorParams(): Array<Class<*>> =
                (historyCopy.map { it.klass.java } +
                        (scriptArgs?.mapIndexed { i, it -> scriptArgsTypes?.getOrNull(i) ?: it?.javaClass ?: Any::class.java } ?: emptyList())
                        ).toTypedArray()

        fun getConstructorArgs() = (historyCopy.map { it.instance } + scriptArgs.orEmpty()).toTypedArray()

        val constructorParams: Array<Class<*>> = getConstructorParams()
        val constructorArgs: Array<Any?> = getConstructorArgs()

        val scriptInstanceConstructor = scriptClass.getConstructor(*constructorParams)
        val scriptInstance =
                try {
                    if (invokeWrapper != null) invokeWrapper.invoke { scriptInstanceConstructor.newInstance(*constructorArgs)  }
                    else scriptInstanceConstructor.newInstance(*constructorArgs)
                } catch (e: Throwable) {
                    // ignore everything in the stack trace until this constructor call
                    return ResettableReplEvaluator.Response.Error.Runtime(compiledLoadedClassesHistory.historyAsSource(),
                            renderReplStackTrace(e.cause!!, startFromMethodName = "${scriptClass.name}.<init>"), e as? Exception)
                }

        compiledLoadedClassesHistory.add(compileResult.compiledCodeLine, EvalClassWithInstanceAndLoader(scriptClass.kotlin, scriptInstance, classLoader))

        val rvField = scriptClass.getDeclaredField(SCRIPT_RESULT_FIELD_NAME).apply { isAccessible = true }
        val rv: Any? = rvField.get(scriptInstance)

        return if (compileResult.hasResult) ResettableReplEvaluator.Response.ValueResult(compiledLoadedClassesHistory.historyAsSource(), rv)
        else ResettableReplEvaluator.Response.UnitResult(compiledLoadedClassesHistory.historyAsSource())
    }

    override val lastEvaluatedScript: EvalClassWithInstanceAndLoader? get() =
    evalStateLock.read { compiledLoadedClassesHistory.lastValue() }

    companion object {
        private val SCRIPT_RESULT_FIELD_NAME = "\$\$result"
    }
}
