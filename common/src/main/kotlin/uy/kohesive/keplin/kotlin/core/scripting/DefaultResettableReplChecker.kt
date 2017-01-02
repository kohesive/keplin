package uy.kohesive.keplin.kotlin.core.scripting

import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.AnalyzerWithCompilerReport
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.repl.ReplCodeLine
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.repl.messages.DiagnosticMessageHolder
import org.jetbrains.kotlin.cli.jvm.repl.messages.ReplTerminalDiagnosticMessageHolder
import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.com.intellij.openapi.vfs.CharsetToolkit
import org.jetbrains.kotlin.com.intellij.psi.PsiFileFactory
import org.jetbrains.kotlin.com.intellij.psi.impl.PsiFileFactoryImpl
import org.jetbrains.kotlin.com.intellij.testFramework.LightVirtualFile
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.parsing.KotlinParserDefinition
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.script.KotlinScriptDefinition

open class DefaultResettableReplChecker(
        disposable: Disposable,
        val scriptDefinition: KotlinScriptDefinition,
        val compilerConfiguration: CompilerConfiguration,
        messageCollector: MessageCollector
) : ResettableReplChecker {
    protected val environment = run {
        compilerConfiguration.apply {
            add(JVMConfigurationKeys.SCRIPT_DEFINITIONS, scriptDefinition)
            put<MessageCollector>(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, messageCollector)
            put(JVMConfigurationKeys.RETAIN_OUTPUT_IN_MEMORY, true)
        }
        KotlinCoreEnvironment.createForProduction(disposable, compilerConfiguration, EnvironmentConfigFiles.JVM_CONFIG_FILES)
    }

    protected val psiFileFactory: PsiFileFactoryImpl = PsiFileFactory.getInstance(environment.project) as PsiFileFactoryImpl

    // "line" - is the unit of evaluation here, could in fact consists of several character lines
    protected class LineState(
            val codeLine: ReplCodeLine,
            val psiFile: KtFile,
            val errorHolder: DiagnosticMessageHolder)

    protected var lineState: LineState? = null

    fun createDiagnosticHolder() = ReplTerminalDiagnosticMessageHolder()

    @Synchronized
    override fun check(codeLine: ReplCodeLine, generation: Long): ResettableReplChecker.Response {
        val scriptFileName = makeSriptBaseName(codeLine, generation)
        val virtualFile =
                LightVirtualFile("${scriptFileName}${KotlinParserDefinition.STD_SCRIPT_EXT}", KotlinLanguage.INSTANCE, codeLine.code).apply {
                    charset = CharsetToolkit.UTF8_CHARSET
                }
        val psiFile: KtFile = psiFileFactory.trySetupPsiForFile(virtualFile, KotlinLanguage.INSTANCE, true, false) as KtFile?
                ?: error("Script file not analyzed at line ${codeLine.no}: ${codeLine.code}")

        val errorHolder = createDiagnosticHolder()

        val syntaxErrorReport = AnalyzerWithCompilerReport.reportSyntaxErrors(psiFile, errorHolder)

        if (!syntaxErrorReport.isHasErrors) {
            lineState = LineState(codeLine, psiFile, errorHolder)
        }

        return when {
            syntaxErrorReport.isHasErrors && syntaxErrorReport.isAllErrorsAtEof -> ResettableReplChecker.Response.Incomplete()
            syntaxErrorReport.isHasErrors -> ResettableReplChecker.Response.Error(errorHolder.renderedDiagnostics)
            else -> ResettableReplChecker.Response.Ok()
        }
    }
}
