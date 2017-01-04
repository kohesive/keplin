package uy.kohesive.keplin.kotlin.core.scripting

import org.jetbrains.kotlin.cli.common.repl.ReplCodeLine
import java.io.Serializable
import java.util.concurrent.ConcurrentLinkedDeque

/*
   Not thread safe, the caller is assumed to lock access.  Even though ConcurrentLinkedDeque is used, there are mutliple
   actions that require locking.
 */
class ResettableReplHistory<T> : Serializable {
    private val history = ConcurrentLinkedDeque<Pair<CompiledReplCodeLine, T>>()

    init {
        assert(isValid())
    }

    fun isValid() = true

    fun add(line: CompiledReplCodeLine, value: T) {
        history.add(line to value)
    }

    /* resets back to a previous line number and returns the lines removed */
    fun resetToLine(lineNumber: Int): List<Pair<ReplCodeLine, T>> {
        val removed = arrayListOf<Pair<ReplCodeLine, T>>()

        while ((history.peekLast()?.first?.source?.no ?: -1) > lineNumber) {
            removed.add(history.removeLast().let { Pair(it.first.source, it.second) })
        }
        return removed
    }

    /* remove last line only if it is the line we think it is */
    fun removeLast(line: CompiledReplCodeLine): Boolean {
        return if (history.peekLast().first == line) {
            history.removeLast()
            true
        } else {
            false
        }
    }

    fun resetToLine(line: ReplCodeLine): List<Pair<ReplCodeLine, T>> {
        return resetToLine(line.no)
    }

    fun resetToLine(line: CompiledReplCodeLine): List<Pair<ReplCodeLine, T>> {
        return resetToLine(line.source.no)
    }

    fun lastCodeLine(): CompiledReplCodeLine? = history.peekLast()?.first
    fun lastValue(): T? = history.peekLast()?.second

    fun checkHistoryIsInSync(compareHistory: List<ReplCodeLine>?): Boolean {
        return firstMismatchingHistory(compareHistory) == null
    }

    // return from the compareHistory the first line that does not match or null
    fun firstMismatchingHistory(compareHistory: List<ReplCodeLine>?): Int? {
        if (compareHistory == null) return null

        val firstMismatch = history.zip(compareHistory).firstOrNull { it.first.first.source != it.second }?.second?.no

        if (compareHistory.size == history.size) return firstMismatch
        if (compareHistory.size > history.size) return compareHistory[history.size].no
        return history.toList()[compareHistory.size].first.source.no
    }

    fun historyAsSource(): List<ReplCodeLine> = history.map { it.first.source }
    fun copyValues(): List<T> = history.map { it.second }

    companion object {
        private val serialVersionUID: Long = 8328353000L
    }
}

