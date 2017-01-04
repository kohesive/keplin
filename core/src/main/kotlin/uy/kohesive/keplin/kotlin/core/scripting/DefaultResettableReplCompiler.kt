package uy.kohesive.keplin.kotlin.core.scripting

import com.intellij.openapi.Disposable
import org.jetbrains.kotlin.cli.common.messages.AnalyzerWithCompilerReport
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.repl.CompiledClassData
import org.jetbrains.kotlin.cli.common.repl.ReplCodeLine
import org.jetbrains.kotlin.cli.jvm.config.JvmClasspathRoot
import org.jetbrains.kotlin.codegen.ClassBuilderFactories
import org.jetbrains.kotlin.codegen.KotlinCodegenFacade
import org.jetbrains.kotlin.codegen.state.GenerationState
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.descriptors.ScriptDescriptor
import org.jetbrains.kotlin.script.KotlinScriptExternalDependencies
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

open class DefaultResettableReplCompiler(disposable: Disposable,
                                         scriptDefinition: KotlinScriptDefinitionEx,
                                         compilerConfiguration: CompilerConfiguration,
                                         messageCollector: MessageCollector,
                                         stateLock: ReentrantReadWriteLock = ReentrantReadWriteLock()) :
        ResettableReplCompiler,
        DefaultResettableReplChecker(disposable, scriptDefinition, compilerConfiguration, messageCollector, stateLock) {

    private val analyzerEngine = DefaultResettableReplAnalyzer(environment, stateLock)

    private var lastDependencies: KotlinScriptExternalDependencies? = null

    private val descriptorsHistory = ResettableReplHistory<ScriptDescriptor>()

    private val generation = AtomicLong(1)

    override fun resetToLine(lineNumber: Int): List<ReplCodeLine> {
        return stateLock.write {
            generation.incrementAndGet()
            val removedCompiledLines = descriptorsHistory.resetToLine(lineNumber)
            val removedAnalyzedLines = analyzerEngine.resetToLine(lineNumber)

            removedCompiledLines.zip(removedAnalyzedLines).forEach {
                if (it.first.first != it.second) {
                    throw IllegalStateException("History mistmatch when resetting lines")
                }
            }

            removedCompiledLines
        }.map { it.first }
    }

    override val compilationHistory: List<ReplCodeLine> get() = stateLock.read { descriptorsHistory.historyAsSource() }

    @Synchronized
    override fun compile(codeLine: ReplCodeLine, verifyHistory: List<ReplCodeLine>?): ResettableReplCompiler.Response {
        stateLock.write {
            val firstMismatch = descriptorsHistory.firstMismatchingHistory(verifyHistory)
            if (firstMismatch != null) {
                return@compile ResettableReplCompiler.Response.HistoryMismatch(descriptorsHistory.historyAsSource(), firstMismatch)
            }

            val currentGeneration = generation.get()

            val (psiFile, errorHolder) = run {
                if (lineState == null || lineState!!.codeLine != codeLine) {
                    val res = check(codeLine, currentGeneration)
                    when (res) {
                        is ResettableReplChecker.Response.Incomplete -> return@compile ResettableReplCompiler.Response.Incomplete(descriptorsHistory.historyAsSource())
                        is ResettableReplChecker.Response.Error -> return@compile ResettableReplCompiler.Response.Error(descriptorsHistory.historyAsSource(), res.message, res.location)
                        is ResettableReplChecker.Response.Ok -> DO_NOTHING()
                    }
                }
                Pair(lineState!!.psiFile, lineState!!.errorHolder)
            }

            val newDependencies = scriptDefinition.getDependenciesFor(psiFile, environment.project, lastDependencies)
            var classpathAddendum: List<File>? = null
            if (lastDependencies != newDependencies) {
                lastDependencies = newDependencies
                classpathAddendum = newDependencies?.let { environment.updateClasspath(it.classpath.map(::JvmClasspathRoot)) }
                // TODO: the classpath for compilation should reset back to the correct point when resetting, only the evaluator resets now
                //       ...not sure it is possible environment doesn't have a removeClasspath option.  The line above is the issue that is
                //       not reset
            }

            val analysisResult = analyzerEngine.analyzeReplLine(psiFile, codeLine)
            AnalyzerWithCompilerReport.reportDiagnostics(analysisResult.diagnostics, errorHolder)
            val scriptDescriptor = when (analysisResult) {
                is DefaultResettableReplAnalyzer.ReplLineAnalysisResult.WithErrors -> return ResettableReplCompiler.Response.Error(descriptorsHistory.historyAsSource(), errorHolder.renderedDiagnostics)
                is DefaultResettableReplAnalyzer.ReplLineAnalysisResult.Successful -> analysisResult.scriptDescriptor
                else -> error("Unexpected result ${analysisResult.javaClass}")
            }

            val state = GenerationState(
                    psiFile.project,
                    ClassBuilderFactories.binaries(false),
                    analyzerEngine.module,
                    analyzerEngine.trace.bindingContext,
                    listOf(psiFile),
                    compilerConfiguration
            )
            state.replSpecific.scriptResultFieldName = SCRIPT_RESULT_FIELD_NAME
            state.replSpecific.earlierScriptsForReplInterpreter = descriptorsHistory.copyValues()
            state.beforeCompile()
            KotlinCodegenFacade.generatePackage(
                    state,
                    psiFile.script!!.getContainingKtFile().packageFqName,
                    setOf(psiFile.script!!.getContainingKtFile()),
                    org.jetbrains.kotlin.codegen.CompilationErrorHandler.THROW_EXCEPTION)

            val generatedClassname = makeSriptBaseName(codeLine, currentGeneration)
            val compiledCodeLine = CompiledReplCodeLine(generatedClassname, codeLine)
            descriptorsHistory.add(compiledCodeLine, scriptDescriptor)

            return ResettableReplCompiler.Response.CompiledClasses(descriptorsHistory.historyAsSource(),
                    compiledCodeLine,
                    generatedClassname,
                    state.factory.asList().map { CompiledClassData(it.relativePath, it.asByteArray()) },
                    state.replSpecific.hasResult,
                    classpathAddendum ?: emptyList())
        }
    }

    companion object {
        private val SCRIPT_RESULT_FIELD_NAME = "\$\$result"
    }
}