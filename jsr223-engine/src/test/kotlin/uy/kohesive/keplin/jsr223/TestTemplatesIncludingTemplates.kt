package uy.kohesive.keplin.jsr223

import org.junit.Test
import uy.kohesive.keplin.jsr223.core.scripting.AbstractEngineFactory
import uy.kohesive.keplin.kotlin.core.scripting.ScriptArgsWithTypes
import java.io.FileNotFoundException
import java.io.StringWriter
import java.util.concurrent.ConcurrentHashMap
import javax.script.*
import javax.script.ScriptContext.ENGINE_SCOPE
import kotlin.test.assertEquals

class TestTemplatesIncludingTemplates {
    @Test
    fun mimicSpringTemplateIdea() {
        val factory = ScriptEngineManager()
        val engine = factory.getEngineByName(MimicSpringKotlinScriptEngineFactory.jsr223EngineName)

        val capture = StringWriter()
        engine.context.writer = capture

        val output = engine.eval("""
           include("index")
        """) as String

        val expected = clean("""
                <html>
                <head>
                    <title>Hi, I'm the page</title>
                </head>
                </html>
                <body>
                <div>Top of page</div>
                <p>Body stuff</p>
                <p>Path separator char is ':'</p>
                <div>Footer</div>
                </body>
                </html>
        """)

        assertEquals(clean(expected), clean(output))
    }

    fun clean(input: String): String {
        return input.split('\n').map { it.trim() }.filterNot { it.isBlank() }.joinToString("\n")
    }
}

interface ScriptFindingService {
    fun include(templateName: String, context: ScriptContext): String
}

class ScriptFinder() : ScriptFindingService {
    val cache: ConcurrentHashMap<String, CompiledScript> = ConcurrentHashMap()

    private fun compileTemplate(code: String): CompiledScript {
        val codeLines = code.split('\n')
        val imports = codeLines.takeWhile { it.isBlank() || it.startsWith("import ") || it.startsWith("import\t") }
        val body = codeLines.drop(imports.size)

        val finalCode = imports.joinToString("\n") + "\"\"\"\n${body.joinToString("\n")}\n\"\"\""
        val compiler = ScriptEngineManager().getEngineByName(MimicSpringKotlinScriptEngineFactory.jsr223EngineName) as Compilable
        return compiler.compile(finalCode)
    }

    @Suppress("DEPRECATION")
    override fun include(templateName: String, context: ScriptContext): String {
        val compileTemplate = cache.computeIfAbsent(templateName) {
            val resourceName = "templates/$templateName.ktml"
            val resource = ClassLoader.getSystemClassLoader().getResourceAsStream(resourceName) ?: throw FileNotFoundException("Cannot find template '$resourceName'")
            compileTemplate(resource.bufferedReader().use { it.readText() })
        }
        return compileTemplate.eval(context) as String
    }
}

abstract class MimicSpringScriptTemplate(private val context: ScriptContext, private val scriptFinder: ScriptFindingService) {
    protected val bindings: Bindings = context.getBindings(ENGINE_SCOPE)
    protected fun include(templateName: String): String {
        val result = scriptFinder.include(templateName, context)
        return result
    }
}

val MimicSpringScriptTemplateArgTypes = arrayOf(ScriptContext::class, ScriptFindingService::class)
val MimicSpringScriptTemplateEmptyArgs
        = ScriptArgsWithTypes(arrayOf<Any?>(null, null), KeplinKotlinJsr223ScriptTemplateArgTypes)

class MimicSpringKotlinScriptEngineFactory : AbstractEngineFactory() {
    override fun getScriptEngine(): ScriptEngine {
        return MimicSpringKotlinScriptEngine(this).apply { fixupArgsForScriptTemplate() }
    }

    override fun getEngineName(): String = "Mimic Spring Include Stuff Kotlin Scripting Engine"
    override fun getNames(): List<String> = listOf(jsr223EngineName)

    companion object {
        val jsr223EngineName = MimicSpringKotlinScriptEngine.jsr223EngineName
    }
}

class MimicSpringKotlinScriptEngine(factory: MimicSpringKotlinScriptEngineFactory,
                                    defaultImports: List<String> = emptyList())
    : CompilableJsr223ReplEngine(factory, defaultImports) {

    override val scriptTemplate = MimicSpringScriptTemplate::class
    override val defaultEmptyArgs = MimicSpringScriptTemplateEmptyArgs

    private val scriptFinderService = ScriptFinder()

    override fun baseArgsForScriptTemplate(context: ScriptContext): ScriptArgsWithTypes? {
        return ScriptArgsWithTypes(arrayOf<Any?>(context, scriptFinderService), MimicSpringScriptTemplateArgTypes)
    }

    companion object {
        val jsr223EngineName = "keplin-kotin-mimic-springy"
    }
}