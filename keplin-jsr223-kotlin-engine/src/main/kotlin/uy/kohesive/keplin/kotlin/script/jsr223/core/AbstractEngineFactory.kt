package uy.kohesive.keplin.kotlin.script.jsr223.core

import org.jetbrains.kotlin.config.KotlinCompilerVersion
import javax.script.ScriptEngine
import javax.script.ScriptEngineFactory

abstract class AbstractEngineFactory : ScriptEngineFactory {
    override fun getLanguageName(): String = "kotlin"
    override fun getLanguageVersion(): String = KotlinCompilerVersion.VERSION
    override fun getEngineVersion(): String = KotlinCompilerVersion.VERSION
    override fun getExtensions(): List<String> = listOf("kts")
    override fun getMimeTypes(): List<String> = listOf("text/x-kotlin")

    abstract override fun getEngineName(): String
    abstract override fun getNames(): List<String>
    open fun getThreadingModel(): String = "MULTITHREADED"

    override fun getOutputStatement(toDisplay: String?): String = "print(\"$toDisplay\")"
    override fun getMethodCallSyntax(obj: String, m: String, vararg args: String): String = "$obj.$m(${args.joinToString()})"

    override fun getProgram(vararg statements: String): String {
        val sep = System.getProperty("line.separator")
        return statements.joinToString(sep) + sep
    }

    override fun getParameter(key: String?): Any? =
            when (key) {
                ScriptEngine.NAME -> engineName
                ScriptEngine.LANGUAGE -> languageName
                ScriptEngine.LANGUAGE_VERSION -> languageVersion
                ScriptEngine.ENGINE -> engineName
                ScriptEngine.ENGINE_VERSION -> engineVersion
                "THREADING", "javax.script.threading" -> getThreadingModel()
                else -> null
            }
}