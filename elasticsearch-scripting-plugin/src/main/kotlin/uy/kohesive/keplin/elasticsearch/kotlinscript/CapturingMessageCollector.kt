package uy.kohesive.keplin.elasticsearch.kotlinscript

import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector

class CapturingMessageCollector : MessageCollector {
    val messages: ArrayList<Message> = arrayListOf()

    override fun clear() {
        messages.clear()
    }

    override fun report(severity: CompilerMessageSeverity, message: String, location: CompilerMessageLocation) {
        messages.add(Message(severity, message, location))
    }

    override fun hasErrors(): Boolean = messages.any { it.severity.isError }

    data class Message(val severity: CompilerMessageSeverity, val message: String, val location: CompilerMessageLocation)
}