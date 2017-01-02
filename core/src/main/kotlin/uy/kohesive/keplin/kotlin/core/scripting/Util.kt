package uy.kohesive.keplin.kotlin.core.scripting

import org.jetbrains.kotlin.cli.common.repl.ReplClassLoader
import org.jetbrains.kotlin.cli.common.repl.ReplCodeLine
import java.io.File
import java.net.URLClassLoader

fun makeReplClassLoader(baseClassloader: ClassLoader?, baseClasspath: Iterable<File>) =
        ReplClassLoader(URLClassLoader(baseClasspath.map { it.toURI().toURL() }.toTypedArray(), baseClassloader))

// first letter must be upper case
fun makeSriptBaseName(codeLine: ReplCodeLine, generation: Long) =
        "Line_${codeLine.no}" + if (generation > 1) "_gen_${generation}" else ""

fun DO_NOTHING(): Unit = Unit