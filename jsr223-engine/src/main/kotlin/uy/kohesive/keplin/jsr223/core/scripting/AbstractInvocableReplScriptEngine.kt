package uy.kohesive.keplin.jsr223.core.scripting

import com.google.common.base.Throwables
import org.jetbrains.kotlin.cli.common.repl.InvokeWrapper
import org.jetbrains.kotlin.utils.tryCreateCallableMapping
import javax.script.Invocable
import javax.script.ScriptEngineFactory
import javax.script.ScriptException
import kotlin.reflect.*


abstract class AbstractInvocableReplScriptEngine(factory: ScriptEngineFactory,
                                                 defaultImports: List<String>)
    : AbstractReplScriptEngine(factory, defaultImports), Invocable {

    override fun invokeFunction(name: String?, vararg args: Any?): Any? {
        if (name == null) throw java.lang.NullPointerException("function name cannot be null")
        val (klass, instance) = engine.lastEvaluatedScript ?: throw IllegalArgumentException("no script ")
        return invokeImpl(klass, instance!!, name, args, invokeWrapper = null)
    }

    override fun invokeMethod(thiz: Any?, name: String?, vararg args: Any?): Any? {
        if (name == null) throw java.lang.NullPointerException("method name cannot be null")
        if (thiz == null) throw IllegalArgumentException("cannot invoke method on the null object")
        return invokeImpl(thiz.javaClass.kotlin, thiz, name, args, invokeWrapper = null)
    }

    override fun <T : Any> getInterface(clasz: Class<T>?): T? {
        val (_, instance) = engine.lastEvaluatedScript ?: throw IllegalArgumentException("no script ")
        return getInterface(instance, clasz)
    }

    override fun <T : Any> getInterface(thiz: Any?, clasz: Class<T>?): T? {
        if (thiz == null) throw IllegalArgumentException("object cannot be null")
        if (clasz == null) throw IllegalArgumentException("class object cannot be null")
        if (!clasz.isInterface) throw IllegalArgumentException("expecting interface")

        // TODO: need to create a class that delegates the interface methods to the last
        // line instance methods of same signature.  Not just cast it as this does...

        TODO("This doesn't work correctly yet")
        return clasz.kotlin.safeCast(thiz)
    }

    private fun invokeImpl(receiverClass: KClass<*>, receiverInstance: Any, name: String, args: Array<out Any?>, invokeWrapper: InvokeWrapper?): Any? {

        val candidates = receiverClass.functions.filter { it.name == name }
        val (fn, mapping) = candidates.findMapping(listOf<Any?>(receiverInstance) + args) ?:
                throw NoSuchMethodException("no suitable function '$name' found")
        val res = try {
            invokeWrapper?.invoke {
                fn.callBy(mapping)
            } ?: fn.callBy(mapping)
        } catch (e: Throwable) {
            // ignore everything in the stack trace until this constructor call
            throw ScriptException(renderReplStackTrace(e.cause!!, startFromMethodName = fn.name))
        }
        return if (fn.returnType.classifier == Unit::class) Unit else res
    }

    private fun Iterable<KFunction<*>>.findMapping(args: List<Any?>): Pair<KFunction<*>, Map<KParameter, Any?>>? {
        for (fn in this) {
            val mapping = tryCreateCallableMapping(fn, args)
            if (mapping != null) return fn to mapping
        }
        return null
    }


    fun renderReplStackTrace(cause: Throwable, startFromMethodName: String): String {
        val newTrace = arrayListOf<StackTraceElement>()
        var skip = true
        for ((_, element) in cause.stackTrace.withIndex().reversed()) {
            if ("${element.className}.${element.methodName}" == startFromMethodName) {
                skip = false
            }
            if (!skip) {
                newTrace.add(element)
            }
        }

        val resultingTrace = newTrace.reversed().dropLast(1)

        @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN", "UsePropertyAccessSyntax")
        (cause as java.lang.Throwable).setStackTrace(resultingTrace.toTypedArray())

        return Throwables.getStackTraceAsString(cause)
    }


}
