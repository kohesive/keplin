package uy.kohesive.keplin.jsr223.core.scripting

import uy.kohesive.keplin.kotlin.util.scripting.InOutTrapper
import java.io.Reader
import java.io.Writer
import javax.script.Bindings
import javax.script.ScriptContext
import javax.script.SimpleBindings

open class IoFriendlyScriptContext : ScriptContext {
    protected var _writer: Writer = MarkedFriendlyPrintWriter(InOutTrapper.originalSystemOut)
    protected var _errorWriter: Writer = MarkedFriendlyPrintWriter(InOutTrapper.originalSystemErr)
    protected var _reader: Reader = MarkedFriendlyInputStreamReader(InOutTrapper.originalSystemIn)

    protected var _engineScope: Bindings = SimpleBindings()
    protected var _globalScope: Bindings? = null

    override fun setBindings(bindings: Bindings?, scope: Int) {
        when (scope) {
            ScriptContext.ENGINE_SCOPE -> {
                if (bindings == null) {
                    throw NullPointerException("Engine scope cannot be null.")
                }
                _engineScope = bindings
            }
            ScriptContext.GLOBAL_SCOPE -> _globalScope = bindings
            else -> throw IllegalArgumentException("Invalid scope value.")
        }
    }

    override fun getAttribute(name: String): Any? {
        checkName(name)
        return _engineScope.get(name) ?: _globalScope?.get(name)
    }

    override fun getAttribute(name: String, scope: Int): Any? {
        checkName(name)
        return when (scope) {
            ScriptContext.ENGINE_SCOPE -> _engineScope[name]
            ScriptContext.GLOBAL_SCOPE -> _globalScope?.get(name)
            else -> throw IllegalArgumentException("Illegal scope value.")
        }
    }

    override fun removeAttribute(name: String, scope: Int): Any? {
        checkName(name)
        return when (scope) {
            ScriptContext.ENGINE_SCOPE -> _engineScope.remove(name)
            ScriptContext.GLOBAL_SCOPE -> _globalScope?.remove(name)
            else -> throw IllegalArgumentException("Illegal scope value.")
        }
    }

    override fun setAttribute(name: String, value: Any, scope: Int) {
        checkName(name)
        when (scope) {
            ScriptContext.ENGINE_SCOPE -> _engineScope.put(name, value)
            ScriptContext.GLOBAL_SCOPE -> _globalScope?.put(name, value)
            else -> throw IllegalArgumentException("Illegal scope value.")
        }
    }

    override fun getWriter(): Writer {
        return _writer
    }

    override fun getReader(): Reader {
        return _reader
    }

    override fun setReader(reader: Reader) {
        _reader = reader
    }

    override fun setWriter(writer: Writer) {
        _writer = writer
    }

    override fun getErrorWriter(): Writer {
        return _errorWriter
    }

    override fun setErrorWriter(writer: Writer) {
        _errorWriter = writer
    }

    override fun getAttributesScope(name: String): Int {
        checkName(name)
        if (_engineScope.containsKey(name)) {
            return ScriptContext.ENGINE_SCOPE
        } else if (_globalScope?.containsKey(name) ?: false) {
            return ScriptContext.GLOBAL_SCOPE
        } else {
            return -1
        }
    }

    override fun getBindings(scope: Int): Bindings? {
        if (scope == ScriptContext.ENGINE_SCOPE) {
            return _engineScope
        } else if (scope == ScriptContext.GLOBAL_SCOPE) {
            return _globalScope
        } else {
            throw IllegalArgumentException("Illegal scope value.")
        }
    }

    override fun getScopes(): List<Int> {
        return _scopes
    }

    private fun checkName(name: String) {
        if (name.isEmpty()) {
            throw IllegalArgumentException("name cannot be empty")
        }
    }

    companion object {
        private var _scopes: List<Int> = listOf(ScriptContext.ENGINE_SCOPE, ScriptContext.GLOBAL_SCOPE)
    }
}
