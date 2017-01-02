package uy.kohesive.keplin.kotlin.core.scripting

import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.repl.CompiledClassData
import org.jetbrains.kotlin.cli.common.repl.InvokeWrapper
import org.jetbrains.kotlin.cli.common.repl.ReplCodeLine
import java.io.File
import java.io.Serializable
import kotlin.reflect.KClass

interface ResettableReplChecker {
    fun check(codeLine: ReplCodeLine, generation: Long = 1): Response

    sealed class Response() : Serializable {
        class Ok() : Response()

        class Incomplete() : Response()

        class Error(val message: String,
                    val location: CompilerMessageLocation = CompilerMessageLocation.NO_LOCATION) : Response() {
            override fun toString(): String = "Error(message = \"$message\""
        }

        companion object {
            private val serialVersionUID: Long = 8228357578L
        }
    }
}

interface ResettableReplCompiler : ResettableReplChecker {
    fun compile(codeLine: ReplCodeLine, verifyHistory: List<ReplCodeLine>? = null): Response

    /***
     * Reset back to a given line number, returning the lines that were removed.
     *
     * This must be in sync with the ResettableReplEvaluator being used
     */
    fun resetToLine(lineNumber: Int): List<ReplCodeLine>

    fun resetToLine(line: ReplCodeLine): List<ReplCodeLine> = resetToLine(line.no)

    val compilationHistory: List<ReplCodeLine>

    sealed class Response(val compiledHistory: List<ReplCodeLine>) : Serializable {
        class CompiledClasses(compiledHistory: List<ReplCodeLine>,
                              val compiledCodeLine: CompiledReplCodeLine,
                              val generatedClassname: String,
                              val classes: List<CompiledClassData>,
                              val hasResult: Boolean,
                              val classpathAddendum: List<File>) : Response(compiledHistory)

        class Incomplete(compiledHistory: List<ReplCodeLine>) : Response(compiledHistory)

        class HistoryMismatch(compiledHistory: List<ReplCodeLine>, val lineNo: Int) : Response(compiledHistory)

        class Error(compiledHistory: List<ReplCodeLine>,
                    val message: String,
                    val location: CompilerMessageLocation = CompilerMessageLocation.NO_LOCATION) : Response(compiledHistory) {
            override fun toString(): String = "Error(message = \"$message\""
        }

        companion object {
            private val serialVersionUID: Long = 8228357578L
        }
    }
}

data class CompiledReplCodeLine(val className: String, val source: ReplCodeLine) {
    companion object {
        private val serialVersionUID: Long = 8221337578L
    }
}

interface ResettableReplEvaluatorBase {
    val lastEvaluatedScript: EvalClassWithInstanceAndLoader?
}

data class EvalClassWithInstanceAndLoader(val klass: KClass<*>, val instance: Any, val classLoader: ClassLoader)

interface ResettableReplEvaluator : ResettableReplEvaluatorBase {
    fun eval(compileResult: ResettableReplCompiler.Response.CompiledClasses,
             invokeWrapper: InvokeWrapper?,
             verifyHistory: List<ReplCodeLine> = compileResult.compiledHistory.dropLast(1)): Response

    /***
     * Reset back to a given line number, returning the lines that were removed.
     *
     * This must be in sync with the ResettableReplCompiler being used
     */
    fun resetToLine(lineNumber: Int): List<ReplCodeLine>

    fun resetToLine(line: ReplCodeLine): List<ReplCodeLine> = resetToLine(line.no)

    val evaluationHistory: List<ReplCodeLine>

    sealed class Response(val completedEvalHistory: List<ReplCodeLine>) : Serializable {
        class ValueResult(completedEvalHistory: List<ReplCodeLine>, val value: Any?) : Response(completedEvalHistory) {
            override fun toString(): String = "Result: $value"
        }

        class UnitResult(completedEvalHistory: List<ReplCodeLine>) : Response(completedEvalHistory)

        class Incomplete(completedEvalHistory: List<ReplCodeLine>) : Response(completedEvalHistory)

        class HistoryMismatch(completedEvalHistory: List<ReplCodeLine>, val lineNo: Int) : Response(completedEvalHistory)

        sealed class Error(completedEvalHistory: List<ReplCodeLine>, val message: String) : Response(completedEvalHistory) {
            class Runtime(completedEvalHistory: List<ReplCodeLine>, message: String, val cause: Exception? = null) : Error(completedEvalHistory, message)

            class CompileTime(completedEvalHistory: List<ReplCodeLine>,
                              message: String,
                              val location: CompilerMessageLocation = CompilerMessageLocation.NO_LOCATION) : Error(completedEvalHistory, message)

            override fun toString(): String = "${this::class.simpleName}Error(message = \"$message\""
        }

        companion object {
            private val serialVersionUID: Long = 8228357578L
        }
    }
}
