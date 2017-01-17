package uy.kohesive.keplin.kotlin.core.scripting

import org.jetbrains.kotlin.cli.common.repl.ReplClassLoader
import org.jetbrains.kotlin.cli.common.repl.ReplCodeLine
import java.io.File
import java.net.URLClassLoader
import kotlin.reflect.KClass

fun makeReplClassLoader(baseClassloader: ClassLoader?, baseClasspath: Iterable<File>) =
        ReplClassLoader(URLClassLoader(baseClasspath.map { it.toURI().toURL() }.toTypedArray(), baseClassloader))

// first letter must be upper case
fun makeSriptBaseName(codeLine: ReplCodeLine, generation: Long) =
        "Line_${codeLine.no}" + if (generation > 1) "_gen_${generation}" else ""

fun DO_NOTHING(): Unit = Unit
fun <T> DO_NOTHING(v: T): T = v

val EMPTY_SCRIPT_ARGS: Array<out Any?> = arrayOf(emptyArray<String>())
val EMPTY_SCRIPT_ARGS_TYPES: Array<out KClass<out Any>> = arrayOf(Array<String>::class)

fun ClassLoader.listAllUrlsAsFiles(): List<File> {
    val parents = generateSequence(this) { loader -> loader.parent }.filterIsInstance(URLClassLoader::class.java)
    return parents.fold(emptyList<File>()) { accum, loader ->
        loader.listLocalUrlsAsFiles() + accum
    }.distinct()
}

fun URLClassLoader.listLocalUrlsAsFiles(): List<File> {
    return this.urLs.map { it.toString().removePrefix("file:") }.filterNotNull().map { File(it) }
}

private val zipOrJarRegex = """(?:zip:|jar:file:)(.*)!\/(?:.*)""".toRegex()
private val filePathRegex = """(?:file:)(.*)""".toRegex()

fun zipOrJarUrlToBaseFile(url: String): String? {
    return zipOrJarRegex.find(url)?.let { it.groupValues[1] }
}

fun classFilenameToBaseDir(url: String, resource: String): String? {
    return filePathRegex.find(url)?.let { it.groupValues[1].removeSuffix(resource) }
}

fun <T : Any> KClass<T>.containingClasspath(filterJarName: Regex = ".*".toRegex()): File? {
    val clp = "${qualifiedName?.replace('.', '/')}.class"
    return Thread.currentThread().contextClassLoader.getResources(clp)
            ?.toList()
            ?.map { it.toString() }
            ?.map { url ->
                zipOrJarUrlToBaseFile(url) ?: qualifiedName?.let { classFilenameToBaseDir(url, clp) }
                        ?: throw IllegalStateException("Expecting a local classpath when searching for class: ${qualifiedName}")
            }
            ?.find { filterJarName.matches(it) }
            ?.let { File(it) }
}