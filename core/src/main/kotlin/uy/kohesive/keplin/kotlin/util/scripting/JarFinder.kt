package uy.kohesive.keplin.kotlin.util.scripting

import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import uy.kohesive.keplin.kotlin.core.scripting.ResettableRepl
import java.io.File
import kotlin.reflect.KClass

fun findKotlinJarFiles(includeKotlinCompiler: Boolean = false,
                       includeKotlinEmbeddableCompiler: Boolean = false,
                       includeStdLib: Boolean = true,
                       includeRuntime: Boolean = true): List<File> {
    return listOf(
            if (includeKotlinCompiler && !includeKotlinEmbeddableCompiler) {
                K2JVMCompiler::class.containingClasspath(""".*\/kotlin-compiler-(?!embeddable).*\.jar""".toRegex()) ?:
                        throw IllegalStateException("Cannot find kotlin compiler classpath, which is required")
            } else null,
            if (includeKotlinEmbeddableCompiler) {
                K2JVMCompiler::class.containingClasspath(""".*\/kotlin-compiler-embeddable.*\.jar""".toRegex()) ?:
                        throw IllegalStateException("Cannot find kotlin compiler classpath, which is required")
            } else null,
            if (includeStdLib) {
                Pair::class.containingClasspath(""".*\/kotlin-stdlib.*\.jar""".toRegex()) ?:
                        throw IllegalStateException("Cannot find kotlin stdlib classpath, which is required")
            } else null,
            if (includeRuntime) {
                JvmName::class.containingClasspath(""".*\/kotlin-runtime.*\.jar""".toRegex()) ?:
                        throw IllegalStateException("Cannot find kotlin runtime classpath, which is required")
            } else null
    ).filterNotNull()
}

fun findRequiredScriptingJarFiles(templateClass: KClass<out Any>? = null,
                                  includeScriptEngine: Boolean = false,
                                  includeKotlinCompiler: Boolean = false,
                                  includeKotlinEmbeddableCompiler: Boolean = false,
                                  includeStdLib: Boolean = true,
                                  includeRuntime: Boolean = true,
                                  additionalClasses: List<KClass<out Any>> = emptyList()): List<File> {
    val templateClassJar = if (templateClass != null) templateClass.containingClasspath() ?:
            throw IllegalStateException("Cannot find template classpath, which is required")
            else null
    val additionalClassJars = additionalClasses.map { it.containingClasspath() ?: throw IllegalStateException("Missing JAR for additional class $it }") }
    val scriptEngineClasses = if (includeScriptEngine) ResettableRepl::class.containingClasspath() ?:
                throw IllegalStateException("Cannot find repl engine classpath, which is required")
                else null
    val kotlinJars = findKotlinJarFiles(includeKotlinCompiler, includeKotlinEmbeddableCompiler, includeStdLib, includeRuntime)
    return (listOf(templateClassJar) + additionalClassJars + listOf(scriptEngineClasses) + kotlinJars).filterNotNull().toSet().toList()
}

fun <T : Any> KClass<T>.containingClasspath(filterJarName: Regex = ".*".toRegex()): File? {
    val clp = "${qualifiedName?.replace('.', '/')}.class"
    val zipOrJarRegex = """(?:zip:|jar:file:)(.*)!\/(?:.*)""".toRegex()
    val filePathRegex = """(?:file:)(.*)""".toRegex()
    return Thread.currentThread().contextClassLoader.getResources(clp)
            ?.toList()
            ?.map { it.toString() }
            ?.map { url ->
                zipOrJarRegex.find(url)?.let { it.groupValues[1] }
                        ?: filePathRegex.find(url)?.let { it.groupValues[1].removeSuffix(clp) }
                        ?: throw IllegalStateException("Expecting a local classpath when searching for class: ${qualifiedName}")
            }
            ?.find { filterJarName.matches(it) }
            ?.let { File(it) }
}
