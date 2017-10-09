package uy.kohesive.cuarentena.kotlin.stdlib

import uy.kohesive.cuarentena.Cuarentena
import uy.kohesive.cuarentena.NamedClassBytes
import uy.kohesive.keplin.util.ClassPathUtils
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipInputStream

fun main(args: Array<String>) {
    val cuarentena = Cuarentena()

    val stdLibClasses = ClassPathUtils.findKotlinStdLibJars().flatMap { kotlinStdLib ->
        kotlinStdLib.getClassNames().map { className ->
            classBytesForClass(className, Thread.currentThread().contextClassLoader)
        }
    }

    val verifyResults: Cuarentena.VerifyResults = cuarentena.verifyClassAgainstPolicies(stdLibClasses)
    verifyResults.violations.distinct().sorted().forEach { println(it) }
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