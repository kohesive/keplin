package uy.kohesive.keplin.jsr223

import org.apache.commons.io.input.ReaderInputStream
import org.apache.commons.io.output.WriterOutputStream
import uy.kohesive.keplin.kotlin.util.scripting.RerouteScriptIoInvoker
import java.io.PrintStream
import javax.script.ScriptContext


class ContextRerouteScriptIoInvoker(val context: ScriptContext)
    : RerouteScriptIoInvoker(ReaderInputStream(context.reader),
        PrintStream(WriterOutputStream(context.writer), true),
        PrintStream(WriterOutputStream(context.errorWriter), true))