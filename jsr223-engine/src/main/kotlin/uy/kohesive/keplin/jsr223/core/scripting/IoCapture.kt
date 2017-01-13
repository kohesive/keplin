package uy.kohesive.keplin.jsr223.core.scripting

import org.apache.commons.io.input.ReaderInputStream
import org.apache.commons.io.output.WriterOutputStream
import org.jetbrains.kotlin.cli.common.repl.InvokeWrapper
import uy.kohesive.keplin.kotlin.util.scripting.RerouteScriptIoInvoker
import java.io.*
import javax.script.ScriptContext

fun makeBestIoTrappingInvoker(context: ScriptContext): InvokeWrapper {
    // for recognizable things that are NOT looping back to possible our own thread aware
    // IO trapper, we can do nice thread specific capturing, otherwise assume the worst and
    // use old style unsafe IO trapping.  A caller can easily tag their reader/writers with
    // a marker interface to tell us to do otherwise.
    return if ((context.reader is IoCaptureFriendly
            || context.reader is StringReader
            || context.reader is CharArrayReader
            || context.reader is FileReader) &&
            (context.writer is IoCaptureFriendly
                    || context.writer is StringWriter
                    || context.writer is CharArrayWriter
                    || context.writer is FileWriter) &&
            (context.errorWriter is IoCaptureFriendly
                    || context.writer is StringWriter
                    || context.writer is CharArrayWriter
                    || context.writer is FileWriter)) {
        ContextRerouteScriptIoInvoker(context)
    } else {
        UnfriendlyContextRerouteScriptIoInvoker(context)
    }
}

class UnfriendlyContextRerouteScriptIoInvoker(val context: ScriptContext) : InvokeWrapper {
    override fun <T> invoke(body: () -> T): T {
        // TODO: this is so unsafe for multi-threaded environment, we don't know what each
        // thread is capturing too, but it is likely ok since most threads in a script context
        // would be using the same input and output streams.  The bigger problem is that
        // we might leave it set to something other than the original stdin/out

        // TODO: alternative is that since most people do not change stdin/out we could always
        // set back to the original stdin/out seen at the time of the first call.  But it is hard
        // to determine the best default behavior.

        // The user can wrap their reader/writer with the IoCaptureFriendly interface and then
        // none of this evil will happen.

        val (oldIn, oldOut, oldErr) = synchronized(this.javaClass) {
            val oldIn = System.`in`
            val oldOut = System.out
            val oldErr = System.err
            System.setIn(ReaderInputStream(context.reader))
            System.setOut(PrintStream(WriterOutputStream(context.writer), true))
            System.setErr(PrintStream(WriterOutputStream(context.errorWriter), true))
            Triple(oldIn, oldOut, oldErr)
        }
        try {
            return body()
        } finally {
            synchronized(this.javaClass) {
                System.setIn(oldIn)
                System.setOut(oldOut)
                System.setErr(oldErr)
            }
        }
    }
}

class ContextRerouteScriptIoInvoker(val context: ScriptContext)
    : RerouteScriptIoInvoker(wrapFriendlyReader(context.reader),
        wrapFriendlyWriter(context.writer),
        wrapFriendlyWriter(context.errorWriter))

fun wrapFriendlyReader(reader: Reader): InputStream {
    return if (reader is IoCaptureFriendly) MarkedFriendlyReaderInputStream(reader)
    else ReaderInputStream(reader)
}

fun wrapFriendlyWriter(writer: Writer): PrintStream {
    return if (writer is IoCaptureFriendly) MarkedFriendlyPrintStream(WriterOutputStream(writer), true)
    else PrintStream(WriterOutputStream(writer), true)
}

interface IoCaptureFriendly

class MarkedFriendlyReaderInputStream(reader: Reader) : ReaderInputStream(reader), IoCaptureFriendly
class MarkedFriendlyInputStreamReader(inputStream: InputStream) : InputStreamReader(inputStream), IoCaptureFriendly
class MarkedFriendlyPrintWriter(outputStream: OutputStream) : PrintWriter(outputStream), IoCaptureFriendly
class MarkedFriendlyPrintStream(outputStream: OutputStream, autoFlush: Boolean) : PrintStream(outputStream, autoFlush), IoCaptureFriendly
