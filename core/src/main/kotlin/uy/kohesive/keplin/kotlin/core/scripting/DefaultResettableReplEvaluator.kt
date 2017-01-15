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
                                          val repeatingMode: ReplRepeatingMode = ReplRepeatingMode.REPEAT_ONLY_MOST_RECENT,
                                          val stateLock: ReentrantReadWriteLock = ReentrantReadWriteLock()) :
        ResettableReplEvaluator {

    private val topClassLoader: ReplClassLoader = makeReplClassLoader(baseClassloader, baseClasspath)

    private val history = ResettableReplHistory<EvalClassWithInstanceAndLoader>()

    override fun resetToLine(lineNumber: Int): List<ReplCodeLine> {
        return stateLock.write {
            history.resetToLine(lineNumber)
        }.map { it.first }
    }

    override val evaluationHistory: List<ReplCodeLine> get() = stateLock.read { history.copySources() }

    private class HistoryActions(val effectiveHistory: List<EvalClassWithInstanceAndLoader>,
                                 val verify: (compareHistory: SourceList?) -> Int?,
                                 val addPlaceholder: (line: CompiledReplCodeLine, value: EvalClassWithInstanceAndLoader) -> Unit,
                                 val removePlaceholder: (line: CompiledReplCodeLine) -> Boolean,
                                 val addFinal: (line: CompiledReplCodeLine, value: EvalClassWithInstanceAndLoader) -> Unit,
                                 val processClasses: () -> Pair<ClassLoader, Class<out Any>>)

    override fun eval(compileResult: ResettableReplCompiler.Response.CompiledClasses,
                      invokeWrapper: InvokeWrapper?,
                      verifyHistory: List<ReplCodeLine>,
                      scriptArgs: ScriptArgsWithTypes?): ResettableReplEvaluator.Response {
        stateLock.write {
            val defaultHistoryActor = HistoryActions(
                    effectiveHistory = history.copyValues(),
                    verify = { line -> history.firstMismatchingHistory(line) },
                    addPlaceholder = { line, value -> history.add(line, value) },
                    removePlaceholder = { line -> history.removeLast(line) },
                    addFinal = { line, value -> history.add(line, value) },
                    processClasses = {
                        var mainLineClassName: String? = null
                        val classLoader = makeReplClassLoader(history.lastValue()?.classLoader ?: topClassLoader, compileResult.classpathAddendum)
                        fun classNameFromPath(path: String) = JvmClassName.byInternalName(path.replaceFirst("\\.class$".toRegex(), ""))
                        fun compiledClassesNames() = compileResult.classes.map { classNameFromPath(it.path).fqNameForClassNameWithoutDollars.asString() }
                        val expectedClassName = compileResult.generatedClassname
                        compileResult.classes.filter { it.path.endsWith(".class") }
                                .forEach {
                                    val className = classNameFromPath(it.path)
                                    if (className.internalName == expectedClassName || className.internalName.endsWith("/$expectedClassName")) {
                                        mainLineClassName = className.internalName.replace('/', '.')
                                    }
                                    classLoader.addClass(className, it.bytes)
                                }

                        val scriptClass = try {
                            classLoader.loadClass(mainLineClassName!!)
                        } catch (t: Throwable) {
                            throw Exception("Error loading class $mainLineClassName: known classes: ${compiledClassesNames()}", t)
                        }
                        Pair(classLoader, scriptClass)
                    })

            val historyActor: HistoryActions = when (repeatingMode) {
                ReplRepeatingMode.NONE -> defaultHistoryActor
                ReplRepeatingMode.REPEAT_ONLY_MOST_RECENT -> {
                    val lastItem = history.lastItem()
                    if (lastItem == null || lastItem.first.source != compileResult.compiledCodeLine.source) {
                        defaultHistoryActor
                    } else {
                        val trimmedHistory = ResettableReplHistory(history.copyAll().dropLast(1))
                        HistoryActions(
                                effectiveHistory = trimmedHistory.copyValues(),
                                verify = { trimmedHistory.firstMismatchingHistory(it) },
                                addPlaceholder = { _, _ -> DO_NOTHING() },
                                removePlaceholder = { DO_NOTHING(true) },
                                addFinal = { line, value ->
                                    history.removeLast(line)
                                    history.add(line, value)
                                },
                                processClasses = {
                                    Pair(lastItem.second.classLoader, lastItem.second.klass.java)
                                })
                    }
                }
                ReplRepeatingMode.REPEAT_ANY_PREVIOUS -> {
                    if (history.isEmpty() || !history.contains(compileResult.compiledCodeLine.source)) {
                        defaultHistoryActor
                    } else {
                        val historyCopy = history.copyAll()
                        val matchingItem = historyCopy.first { it.first.source == compileResult.compiledCodeLine.source }
                        val trimmedHistory = ResettableReplHistory(history.copyAll().takeWhile { it != matchingItem })
                        HistoryActions(
                                effectiveHistory = trimmedHistory.copyValues(),
                                verify = { trimmedHistory.firstMismatchingHistory(it) },
                                addPlaceholder = { _, _ -> DO_NOTHING() },
                                removePlaceholder = { DO_NOTHING(true) },
                                addFinal = { line, value ->
                                    val extraLines = history.resetToLine(line)
                                    history.removeLast(line)
                                    history.add(line, value)
                                    extraLines.forEach {
                                        history.add(it.first, it.second)
                                    }
                                },
                                processClasses = {
                                    Pair(matchingItem.second.classLoader, matchingItem.second.klass.java)
                                })
                    }
                }
            }

            val firstMismatch = historyActor.verify(verifyHistory)
            if (firstMismatch != null) {
                return@eval ResettableReplEvaluator.Response.HistoryMismatch(history.copySources(), firstMismatch)
            }

            val (classLoader, scriptClass) = try {
                historyActor.processClasses()
            } catch (e: Exception) {
                return@eval ResettableReplEvaluator.Response.Error.Runtime(history.copySources(),
                        e.message!!, e)
            }

            val useScriptArgs = scriptArgs?.scriptArgs
            val useScriptArgsTypes = scriptArgs?.scriptArgsTypes?.map { it.java }

            val constructorParams: Array<Class<*>> = (historyActor.effectiveHistory.map { it.klass.java } +
                    (useScriptArgs?.mapIndexed { i, it -> useScriptArgsTypes?.getOrNull(i) ?: it?.javaClass ?: Any::class.java } ?: emptyList())
                    ).toTypedArray()
            val constructorArgs: Array<Any?> = (historyActor.effectiveHistory.map { it.instance } + useScriptArgs.orEmpty()).toTypedArray()

            val scriptInstanceConstructor = scriptClass.getConstructor(*constructorParams)

            historyActor.addPlaceholder(compileResult.compiledCodeLine, EvalClassWithInstanceAndLoader(scriptClass.kotlin, null, classLoader, invokeWrapper))

            val scriptInstance =
                    try {
                        if (invokeWrapper != null) invokeWrapper.invoke { scriptInstanceConstructor.newInstance(*constructorArgs) }
                        else scriptInstanceConstructor.newInstance(*constructorArgs)
                    } catch (e: Throwable) {
                        historyActor.removePlaceholder(compileResult.compiledCodeLine)

                        // ignore everything in the stack trace until this constructor call
                        return@eval ResettableReplEvaluator.Response.Error.Runtime(history.copySources(),
                                renderReplStackTrace(e.cause!!, startFromMethodName = "${scriptClass.name}.<init>"), e as? Exception)
                    }

            historyActor.removePlaceholder(compileResult.compiledCodeLine)
            historyActor.addFinal(compileResult.compiledCodeLine, EvalClassWithInstanceAndLoader(scriptClass.kotlin, scriptInstance, classLoader, invokeWrapper))

            val resultField = scriptClass.getDeclaredField(SCRIPT_RESULT_FIELD_NAME).apply { isAccessible = true }
            val resultValue: Any? = resultField.get(scriptInstance)

            return if (compileResult.hasResult) ResettableReplEvaluator.Response.ValueResult(history.copySources(), resultValue)
            else ResettableReplEvaluator.Response.UnitResult(history.copySources())
        }
    }

    override val lastEvaluatedScripts: List<EvalClassWithInstanceAndLoader> get() {
        return stateLock.read { history.copyValues() }
    }

    companion object {
        private val SCRIPT_RESULT_FIELD_NAME = "\$\$result"
    }
}

