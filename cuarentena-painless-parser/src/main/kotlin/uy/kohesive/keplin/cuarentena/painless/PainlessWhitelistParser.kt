package uy.kohesive.keplin.cuarentena.painless

import uy.kohesive.keplin.cuarentena.policy.DefClass
import uy.kohesive.keplin.cuarentena.policy.DefMethodSig

object PainlessWhitelistParser {
    fun readDefinitions(): Map<String, DefClass> {
        val classSigRegex = """^class\s+([\w\_][\w\_\d\.\$]*)\s+\-\>\s+([\w\_][\w\_\d\.\$]*)(?:\s+extend[s]?\s+([\w\_][\w\_\d\.\,\$]*)\s*)?\s*\{$""".toRegex()
        val methodPartsRegex = """^([\w\_][\w\_\d\.]*(?:\[\])*)\s+((?:[\w\_][\w\_\d\.]*|\<init\>)\*?)\(([\w\_][\w\_\d\.\,\[\]]*)?\)[;]?$""".toRegex()
        val propertyPartsRegex = """^([\w\_][\w\_\d\.]*(?:\[\])*)\s+([\w\_][\w\_\d\.]*\*?)$""".toRegex()

        val painlessPackage = "org.elasticsearch.painless".replace('.', '/')
        val definitionResources = DEFINITION_FILES.map { Thread.currentThread().contextClassLoader.getResourceAsStream("$painlessPackage/$it") }
        val painlessStructs = definitionResources.map { it.bufferedReader() }.map { stream ->
            val fileClasses = mutableListOf<DefClass>()
            var currentClass: DefClass? = null
            val signatures = mutableListOf<DefMethodSig>()

            stream.useLines { lines ->
                lines.filterNot { it.isBlank() }.map { it.trim() }.filterNot { it.startsWith('#') }.forEach { line ->
                    if (line.startsWith("class ")) {
                        val parts = classSigRegex.matchEntire(line)
                        if (parts == null || parts.groups.size < 2) throw IllegalStateException("Invalid struct definition [ $line ]")

                        val painlessClassName = parts.groups[1]!!.value
                        val javaRawClassName = parts.groups[2]!!.value
                        val javaClassName = when (javaRawClassName) {
                            "void" -> Unit::class.java.name
                            "boolean" -> Boolean::class.javaPrimitiveType!!.name
                            "byte" -> Byte::class.javaPrimitiveType!!.name
                            "short" -> Short::class.javaPrimitiveType!!.name
                            "char" -> Char::class.javaPrimitiveType!!.name
                            "int" -> Int::class.javaPrimitiveType!!.name
                            "long" -> Long::class.javaPrimitiveType!!.name
                            "float" -> Float::class.javaPrimitiveType!!.name
                            "double" -> Double::class.javaPrimitiveType!!.name
                            else -> javaRawClassName
                        }

                        currentClass = DefClass(painlessClassName, javaClassName, emptyList())
                    } else if (line.startsWith("}")) {
                        fileClasses.add(currentClass!!.copy(signatures = signatures.toList()))
                        signatures.clear()
                        currentClass = null
                    } else {
                        val parts = methodPartsRegex.matchEntire(line)
                        if (parts != null) {
                            if (parts.groups.size < 2) {
                                throw IllegalStateException("Invalid method definition [ $currentClass => $line ]")
                            }
                            val returnType = parts.groups[1]!!.value
                            val methodName = parts.groups[2]!!.value
                            val paramTypes = parts.groups[3]?.value?.split(',') ?: emptyList()

                            signatures.add(DefMethodSig(returnType, methodName, paramTypes))
                        } else {
                            val propParts = propertyPartsRegex.matchEntire(line)
                            if (propParts == null || propParts.groups.size < 2) {
                                throw IllegalStateException("Invalid property definition [ $currentClass => $line ]")
                            }
                            val returnType = propParts.groups[1]!!.value
                            val methodName = propParts.groups[2]!!.value
                            signatures.add(DefMethodSig(returnType, methodName, emptyList(), true))
                        }
                    }
                }
            }
            fileClasses
        }.flatten()

        val structsByPainlessName = painlessStructs.map { it.painlessName to it }.toMap()
        // val structsByJavaName = painlessStructs.map { it.javaName to it }.toMap()

        fun lookup(painlessName: String): String {
            val baseName = painlessName.substringBefore('[')
            val suffix = painlessName.removePrefix(baseName)

            val javaName = when (baseName) {
                "void" -> Unit::class.java.name
                "def" -> Any::class.java.name
                "boolean" -> Boolean::class.javaPrimitiveType!!.name
                "byte" -> Byte::class.javaPrimitiveType!!.name
                "short" -> Short::class.javaPrimitiveType!!.name
                "char" -> Char::class.javaPrimitiveType!!.name
                "int" -> Int::class.javaPrimitiveType!!.name
                "long" -> Long::class.javaPrimitiveType!!.name
                "float" -> Float::class.javaPrimitiveType!!.name
                "double" -> Double::class.javaPrimitiveType!!.name
                else -> structsByPainlessName.get(baseName)?.javaName
                        ?: throw IllegalStateException("Invalid reference to non-existanct struct $painlessName")
            }
            return javaName + suffix
        }

        val otherSigsByJavaName = extraAllowedSymbols.map { it.javaName to it }.toMap()

        val structsWithJavaTypes = painlessStructs.map {
            val signatures = it.signatures.map { sig -> sig.copy(returnType = lookup(sig.returnType), paramTypes = sig.paramTypes.map { lookup(it) }) }
            val mergedSignatures = otherSigsByJavaName.get(it.javaName)?.signatures?.plus(signatures) ?: signatures
            it.copy(signatures = mergedSignatures)
        }

        return (extraAllowedSymbols + structsWithJavaTypes).map { def ->
            listOf(def.javaName to def) + def.signatures.map { sig ->
                val temp = if (sig.isProperty) {
                    listOf("${def.javaName}.get${sig.methodName.upperFirst()}()${sig.returnType.javaSigPart()}",
                            "${def.javaName}.is${sig.methodName.upperFirst()}()${sig.returnType.javaSigPart()}",
                            "${def.javaName}.has${sig.methodName.upperFirst()}()${sig.returnType.javaSigPart()}",
                            "${def.javaName}.set${sig.methodName.upperFirst()}(${sig.returnType.javaSigPart()})V")
                } else {
                    listOf("${def.javaName}.${sig.methodName}(${sig.paramTypes.map { it.javaSigPart() }.joinToString("")})${sig.returnType.javaSigPart()}") +
                            if (sig.methodName == "<init>") listOf("${def.javaName}.${sig.methodName}(${sig.paramTypes.map { it.javaSigPart() }.joinToString("")})V") else emptyList()
                    // previous line is adding the extra <init>()V signature
                }
                temp.map { it to def }
            }.flatten()
        }.flatten().toMap()
    }

    private val DEFINITION_FILES = listOf("org.elasticsearch.txt",
            "java.lang.txt",
            "java.math.txt",
            "java.text.txt",
            "java.time.txt",
            "java.time.chrono.txt",
            "java.time.format.txt",
            "java.time.temporal.txt",
            "java.time.zone.txt",
            "java.util.txt",
            "java.util.function.txt",
            "java.util.regex.txt",
            "java.util.stream.txt",
            "joda.time.txt")

    val extraAllowedSymbols = listOf(
            DefClass("StringBuilder", "java.lang.StringBuilder", listOf(
                    DefMethodSig("java.lang.StringBuilder", "append", listOf("java.lang.String")),
                    DefMethodSig("java.lang.StringBuilder", "append", listOf("int")),
                    DefMethodSig("java.lang.StringBuilder", "append", listOf("long")),
                    DefMethodSig("java.lang.StringBuilder", "append", listOf("char")),
                    DefMethodSig("java.lang.StringBuilder", "append", listOf("double")),
                    DefMethodSig("java.lang.StringBuilder", "append", listOf("float")),
                    DefMethodSig("java.lang.StringBuilder", "append", listOf("byte")),
                    DefMethodSig("java.lang.StringBuilder", "append", listOf("short")),
                    DefMethodSig("java.lang.StringBuilder", "append", listOf("boolean")),
                    DefMethodSig("java.lang.String", "toString", emptyList()))),
            DefClass("ArrayList", "java.util.ArrayList", listOf(
                    DefMethodSig("java.util.ArrayList", "<init>", listOf("int"))))
    )

    private fun String.upperFirst(): String = this.first().toUpperCase() + this.drop(1)
    private fun String.javaSigPart(): String {
        val baseName = this.substringBefore('[')
        val suffix = this.removePrefix(baseName)

        val typeChar = when (baseName) {
            "void" -> 'V'
            "boolean" -> 'Z'
            "byte" -> 'B'
            "short" -> 'S'
            "char" -> 'C'
            "int" -> 'I'
            "long" -> 'L'
            "float" -> 'F'
            "double" -> 'D'
            else -> 'L'
        }

        val bracketCount = suffix.count { it == '[' }

        return "[".repeat(bracketCount) + typeChar + if (typeChar == 'L') baseName + ";" else ""
    }

}