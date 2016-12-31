package uy.kohesive.keplin.common

import org.jetbrains.kotlin.cli.common.messages.AnalyzerWithCompilerReport
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.repl.CompiledClassData
import org.jetbrains.kotlin.cli.common.repl.ReplCodeLine
import org.jetbrains.kotlin.cli.jvm.config.JvmClasspathRoot
import org.jetbrains.kotlin.codegen.ClassBuilderFactories
import org.jetbrains.kotlin.codegen.KotlinCodegenFacade
import org.jetbrains.kotlin.codegen.state.GenerationState
import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.descriptors.ScriptDescriptor
import org.jetbrains.kotlin.script.KotlinScriptDefinition
import org.jetbrains.kotlin.script.KotlinScriptExternalDependencies
import java.io.File
import java.util.concurrent.atomic.AtomicLong

open class DefaultResettableReplCompiler(disposable: Disposable,
                                         scriptDefinition: KotlinScriptDefinition,
                                         compilerConfiguration: CompilerConfiguration,
                                         messageCollector: MessageCollector) : ResettableReplCompiler, DefaultResettableReplChecker(disposable, scriptDefinition, compilerConfiguration, messageCollector) {
    private val analyzerEngine = DefaultResettableReplAnalyzer(environment)

    private var lastDependencies: KotlinScriptExternalDependencies? = null

    private val descriptorsHistory = ResettableReplHistory<ScriptDescriptor>()

    private val generation = AtomicLong(1)

    override fun resetToLine(lineNumber: Int): List<ReplCodeLine> {
        generation.incrementAndGet()
        val removedCompiledLines = descriptorsHistory.resetToLine(lineNumber)
        val removedAnalyzedLines = analyzerEngine.resetToLine(lineNumber)
        // TODO: compare that the lists are the same?
        return removedCompiledLines.map { it.first }
    }

    override val compilationHistory: List<ReplCodeLine> get() = descriptorsHistory.historyAsSource()

    @Synchronized
    override fun compile(codeLine: ReplCodeLine, verifyHistory: List<ReplCodeLine>?): ResettableReplCompiler.Response {
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
                    is ResettableReplChecker.Response.Ok -> {
                    } // continue
                }
            }
            Pair(lineState!!.psiFile, lineState!!.errorHolder)
        }

        val newDependencies = scriptDefinition.getDependenciesFor(psiFile, environment.project, lastDependencies)
        var classpathAddendum: List<File>? = null
        if (lastDependencies != newDependencies) {
            lastDependencies = newDependencies
            classpathAddendum = newDependencies?.let { environment.updateClasspath(it.classpath.map(::JvmClasspathRoot)) }
        }

        val analysisResult = analyzerEngine.analyzeReplLine(psiFile, codeLine)
        AnalyzerWithCompilerReport.Companion.reportDiagnostics(analysisResult.diagnostics, errorHolder)
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

    companion object {
        private val SCRIPT_RESULT_FIELD_NAME = "\$\$result"
    }
}