package uy.kohesive.cuarentena.kotlin.stdlib

import uy.kohesive.cuarentena.Cuarentena
import uy.kohesive.cuarentena.NamedClassBytes
import uy.kohesive.keplin.util.ClassPathUtils
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipInputStream

fun main(args: Array<String>) {
//    val cuarentena = Cuarentena.createBaseJavaPolicyCuarentena() // java-core only
    val cuarentena = Cuarentena() // java-core only

    val stdLibClasses = ClassPathUtils.findKotlinStdLibJars().flatMap { kotlinStdLib ->
        kotlinStdLib.getClassNames().map { className ->
            classBytesForClass(className, Thread.currentThread().contextClassLoader)
        }
    }

    val perClassViolations = cuarentena.verifyClassAgainstPoliciesPerClass(stdLibClasses)
    val violationClasses   = perClassViolations.map { it.violatingClass.className }.toSet()
    val verifiedClasses    = stdLibClasses.map { it.className } - violationClasses

    println("Verified classes:")
    verifiedClasses.sorted().forEach { println(" + $it") }
    println()
    println("Black-listed classes:")
    perClassViolations.sortedBy { it.violatingClass.className }.forEach {
        println(" - ${it.violatingClass.className}:")
        it.violations.forEach { println("   - $it") }
    }
}

fun classBytesForClass(className: String, useClassLoader: ClassLoader): NamedClassBytes {
    return NamedClassBytes(className,
        useClassLoader.getResourceAsStream(className.replace('.', '/') + ".class").use { it.readBytes() })
}

fun File.getClassNames(): ArrayList<String> {
    val classNames = ArrayList<String>()

    FileInputStream(this).use { fis ->
        ZipInputStream(fis).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.endsWith(".class")) {
                    val className = entry.name.replace('/', '.')
                    classNames.add(className.substring(0, className.length - ".class".length))
                }

                entry = zip.nextEntry
            }
        }
    }

    return classNames
}

class StdLibAllowancesGenerator {



}