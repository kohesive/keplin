package uy.kohesive.cuarentena.painless

import uy.kohesive.cuarentena.policy.*
import java.io.File
import java.lang.reflect.AnnotatedType
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Type

class PainlessWhitelistParser() {
    private fun definitionResources() = DEFINITION_FILES.map { Thread.currentThread().contextClassLoader.getResourceAsStream("$painlessPackage/$it") }
    private val classSigRegex = """^class\s+([\w\_][\w\_\d\.\$]*)\s+\-\>\s+([\w\_][\w\_\d\.\$]*)(?:\s+extend[s]?\s+([\w\_][\w\_\d\.\,\$]*)\s*)?\s*\{$""".toRegex()
    private val methodPartsRegex = """^([\w\_][\w\_\d\.]*(?:\[\])*)\s+((?:[\w\_][\w\_\d\.]*|\<init\>)\*?)\(([\w\_][\w\_\d\.\,\[\]]*)?\)[;]?$""".toRegex()
    private val propertyPartsRegex = """^([\w\_][\w\_\d\.]*(?:\[\])*)\s+([\w\_][\w\_\d\.]*\*?)$""".toRegex()
    private val painlessPackage = "org.elasticsearch.painless".replace('.', '/')
    private val debugOut = false

    fun makePolicy(): List<String> = readDefinitions().toPolicy()

    fun writePolicy(output: File): Unit = output.bufferedWriter().use { writer ->
        makePolicy().forEach {
            writer.write(it)
            writer.newLine()
        }
    }

    fun readDefinitions(): AccessPolicies {
        // two passes, first to pick up the painless class => java class names ... second to build policies

        val painlessClassMappings = definitionResources().map { it.bufferedReader() }.map { stream ->
            stream.use { input ->
                input.lineSequence().filterNot { it.isBlank() }.map { it.trim() }.filterNot { it.startsWith('#') }.map { line ->
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
                        painlessClassName to javaClassName
                    } else if (line.startsWith("}")) {
                        // noop this pass
                        null
                    } else {
                        // noop this pass
                        null
                    }
                }.filterNotNull().toList()
            }
        }.flatten().toMap()

        fun getJavaType(painlessName: String): String {
            val baseName = painlessName.substringBefore('[')
            val suffix = painlessName.substring(baseName.length)
            return painlessClassMappings.get(baseName)?.plus(suffix) ?: throw IllegalStateException("Unknown type $baseName")
        }

        fun Class<*>.safeName() = this.typeName
        fun Type.safeName() = this.erasedType().typeName
        fun AnnotatedType.safeName() = this.type.erasedType().typeName
        fun String.asGetterNames(): List<String> = (this.first().toUpperCase() + this.substring(1)).let { listOf("get$it", "is$it", "has$it") }
        fun String.asSetterNames(): List<String> = (this.first().toUpperCase() + this.substring(1)).let { listOf("set$it") }

        var currentClassName: String? = null
        var currentClass: Class<*>? = null

        fun debug(f: () -> Unit) {
            if (debugOut) {
                f()
            }
        }

        fun Method.signature(returnTypeIsDef: Boolean = false, indicesToOverrideWithObject: Set<Int> = emptySet()): String {
            val checkParams = parameterTypes.map { typeToSigPart(it.safeName()) }.mapIndexed { index, param ->
                if (index in indicesToOverrideWithObject) {
                    "Ljava.lang.Object;"
                } else {
                    param
                }
            }
            val checkReturn = if (returnTypeIsDef) {
                "Ljava.lang.Object;"
            } else {
                returnType.let { typeToSigPart(it.safeName()) }
            }

            return "(${checkParams.joinToString("")})${checkReturn}"
        }

        val painlessPolicies: AccessPolicies = definitionResources().map { it.bufferedReader() }.map { stream ->
            stream.use { input ->
                input.lineSequence().filterNot { it.isBlank() }.map { it.trim() }.filterNot { it.startsWith('#') }.map { line ->

                    if (line.startsWith("class ")) {
                        val parts = classSigRegex.matchEntire(line)
                        if (parts == null || parts.groups.size < 2) throw IllegalStateException("Invalid struct definition [ $line ]")

                        val painlessClassName = parts.groups[1]!!.value
                        currentClassName = getJavaType(painlessClassName)
                        currentClass = loadClass(currentClassName!!)
                        listOf(null)
                    } else if (line.startsWith("}")) {
                        currentClassName = null
                        currentClass = null
                        listOf(null)
                    } else {
                        val parts = methodPartsRegex.matchEntire(line)
                        if (parts != null) {
                            if (parts.groups.size < 2) {
                                throw IllegalStateException("Invalid method definition [ $currentClassName => $line ]")
                            }
                            val returnType = parts.groups[1]!!.value
                            val methodName = parts.groups[2]!!.value
                            val paramTypes = parts.groups[3]?.value?.split(',') ?: emptyList()

                            val paramsCount     = paramTypes.size
                            val defParamIndices = paramTypes.mapIndexed { index, paramType ->
                                if (paramType == "def") index else -1
                            }.filterNot { it == -1 }.toSet()
                            val returnTypeIsDef = returnType == "def"

                            val seekParams = paramTypes.map { typeToSigPart(getJavaType(it)) }
                            val seekReturn = returnType.let { typeToSigPart(getJavaType(it)) }
                            val methodSig = "(${seekParams.joinToString("")})${seekReturn}"

                            if (methodName.endsWith('*')) {
                                // TODO: these likely should instead be Kotlin extensions and in a Kotlin specific extension to painless policy
                                println("NOT YET HANDLED (extension): $currentClassName.$methodName$methodSig")
                                /*
                                    java.lang.CharSequence.replaceAll*(Ljava.util.regex.Pattern;Ljava.util.function.Function;)Ljava.lang.String;
                                    java.lang.CharSequence.replaceFirst*(Ljava.util.regex.Pattern;Ljava.util.function.Function;)Ljava.lang.String;
                                    java.lang.Iterable.any*(Ljava.util.function.Predicate;)Z
                                    java.lang.Iterable.asCollection*()Ljava.util.Collection;
                                    java.lang.Iterable.asList*()Ljava.util.List;
                                    java.lang.Iterable.each*(Ljava.util.function.Consumer;)Ljava.lang.Object;
                                    java.lang.Iterable.eachWithIndex*(Ljava.util.function.ObjIntConsumer;)Ljava.lang.Object;
                                    java.lang.Iterable.every*(Ljava.util.function.Predicate;)Z
                                    java.lang.Iterable.findResults*(Ljava.util.function.Function;)Ljava.util.List;
                                    java.lang.Iterable.groupBy*(Ljava.util.function.Function;)Ljava.util.Map;
                                    java.lang.Iterable.join*(Ljava.lang.String;)Ljava.lang.String;
                                    java.lang.Iterable.sum*()D
                                    java.lang.Iterable.sum*(Ljava.util.function.ToDoubleFunction;)D
                                    java.util.Collection.collect*(Ljava.util.function.Function;)Ljava.util.List;
                                    java.util.Collection.collect*(Ljava.util.Collection;Ljava.util.function.Function;)Ljava.lang.Object;
                                    java.util.Collection.find*(Ljava.util.function.Predicate;)Ljava.lang.Object;
                                    java.util.Collection.findAll*(Ljava.util.function.Predicate;)Ljava.util.List;
                                    java.util.Collection.findResult*(Ljava.util.function.Function;)Ljava.lang.Object;
                                    java.util.Collection.findResult*(Ljava.lang.Object;Ljava.util.function.Function;)Ljava.lang.Object;
                                    java.util.Collection.split*(Ljava.util.function.Predicate;)Ljava.util.List;
                                    java.util.List.getLength*()I
                                    java.util.Map.collect*(Ljava.util.function.BiFunction;)Ljava.util.List;
                                    java.util.Map.collect*(Ljava.util.Collection;Ljava.util.function.BiFunction;)Ljava.lang.Object;
                                    java.util.Map.count*(Ljava.util.function.BiPredicate;)I
                                    java.util.Map.each*(Ljava.util.function.BiConsumer;)Ljava.lang.Object;
                                    java.util.Map.every*(Ljava.util.function.BiPredicate;)Z
                                    java.util.Map.find*(Ljava.util.function.BiPredicate;)Ljava.util.Map$Entry;
                                    java.util.Map.findAll*(Ljava.util.function.BiPredicate;)Ljava.util.Map;
                                    java.util.Map.findResult*(Ljava.util.function.BiFunction;)Ljava.lang.Object;
                                    java.util.Map.findResult*(Ljava.lang.Object;Ljava.util.function.BiFunction;)Ljava.lang.Object;
                                    java.util.Map.findResults*(Ljava.util.function.BiFunction;)Ljava.util.List;
                                    java.util.Map.groupBy*(Ljava.util.function.BiFunction;)Ljava.util.Map;
                                    java.util.regex.Matcher.namedGroup*(Ljava.lang.String;)Ljava.lang.String;
                                 */
                                listOf(null)
                            } else {
                                if (methodName == "<init>") {
                                    val constructorName = currentClassName
                                    val constructor = currentClass!!.declaredConstructors
                                            .filter { Modifier.isPublic(it.modifiers) && constructorName == it.name }
                                            .singleOrNull {
                                                val checkParams = it.parameterTypes.map { typeToSigPart(it.safeName()) }
                                                val checkReturn = it.annotatedReturnType.type.let { typeToSigPart(it.safeName()) }
                                                val checkSig1 = "(${checkParams.joinToString("")})${checkReturn}"
                                                debug { println("check:  $currentClassName.$methodName$checkSig1  = ${checkSig1 == methodSig}") }
                                                checkSig1 == methodSig
                                            }
                                    if (constructor == null) {
                                        throw IllegalStateException("Method not found! $currentClassName.$constructorName$methodSig")
                                    }
                                    listOf(PolicyAllowance.ClassLevel.ClassConstructorAccess(currentClassName!!, methodSig, setOf(AccessTypes.call_Class_Constructor)))
                                } else {
                                    val methods = (currentClass!!.declaredMethods + currentClass!!.methods)
                                            .filter { Modifier.isPublic(it.modifiers) && methodName == it.name && paramsCount == it.parameterCount }
                                            .filter {
                                                val checkSig = it.signature(returnTypeIsDef, defParamIndices)
                                                debug { println("check:  $currentClassName.$methodName$checkSig  = ${checkSig == methodSig}") }
                                                checkSig == methodSig
                                            }
                                    if (methods.isEmpty()) {
                                        throw IllegalStateException("Method not found! $currentClassName.$methodName$methodSig")
                                    }

                                    methods.map { method ->
                                        val access = if (Modifier.isStatic(method.modifiers)) AccessTypes.call_Class_Static_Method
                                        else AccessTypes.call_Class_Instance_Method
                                        val signature = method.signature()

                                        signature to PolicyAllowance.ClassLevel.ClassMethodAccess(currentClassName!!, methodName, signature, setOf(access))
                                    }.distinctBy { it.first }.map { it.second }
                                }
                            }
                        } else {
                            val propParts = propertyPartsRegex.matchEntire(line)
                            if (propParts == null || propParts.groups.size < 2) {
                                throw IllegalStateException("Invalid property definition [ $currentClassName => $line ]")
                            }
                            val returnType = propParts.groups[1]!!.value
                            val propertyName = propParts.groups[2]!!.value

                            // TODO: this is error in Painless Definition
                            val seekReturn = if (propertyName == "MIN_CODE_POINT") typeToSigPart(getJavaType("int"))
                            else returnType.let { typeToSigPart(getJavaType(it)) }

                            val propertySig = seekReturn
                            val propertyGetters = propertyName.asGetterNames()
                            val propertySetters = propertyName.asSetterNames()

                            val getter = currentClass!!.declaredMethods
                                    .filter { met -> Modifier.isPublic(met.modifiers) && met.name in propertyGetters }
                                    .firstOrNull {
                                        val checkReturn = it.returnType.let { typeToSigPart(it.safeName()) }
                                        debug { println("check:  $currentClassName.i@$propertyName:$checkReturn  = ${checkReturn == seekReturn}") }
                                        checkReturn == seekReturn
                                    }
                            val setter = currentClass!!.declaredMethods
                                    .filter { met -> Modifier.isPublic(met.modifiers) && met.name in propertySetters }
                                    .firstOrNull {
                                        val checkReturn = it.returnType.let { typeToSigPart(it.safeName()) }
                                        debug { println("check:  $currentClassName.i@$propertyName:$checkReturn  = ${checkReturn == seekReturn}") }
                                        checkReturn == seekReturn
                                    }

                            if (getter != null) {
                                if (Modifier.isStatic(getter.modifiers)) {
                                    val access = setOf(AccessTypes.read_Class_Static_Property) + if (setter != null && Modifier.isStatic(setter.modifiers)) listOf(AccessTypes.write_Class_Static_Property) else emptyList()
                                    listOf(PolicyAllowance.ClassLevel.ClassPropertyAccess(currentClassName!!, propertyName, propertySig, access))
                                } else {
                                    val access = setOf(AccessTypes.read_Class_Instance_Property) + if (setter != null && !Modifier.isStatic(setter.modifiers)) listOf(AccessTypes.write_Class_Instance_Property) else emptyList()
                                    listOf(PolicyAllowance.ClassLevel.ClassPropertyAccess(currentClassName!!, propertyName, propertySig, access))
                                }
                            } else {
                                // sometimes painless accesses fields instead
                                val field = currentClass!!.declaredFields
                                        .filter { Modifier.isPublic(it.modifiers) && propertyName == it.name }
                                        .firstOrNull {
                                            val checkReturn = it.type.let { typeToSigPart(it.safeName()) }
                                            debug { println("check:  $currentClassName.!$propertyName:$checkReturn  = ${checkReturn == seekReturn}") }
                                            checkReturn == seekReturn
                                        }
                                if (field != null) {
                                    val access = when {
                                        Modifier.isStatic(field.modifiers) && Modifier.isFinal(field.modifiers) -> setOf(AccessTypes.read_Class_Static_Field)
                                        Modifier.isStatic(field.modifiers) -> setOf(AccessTypes.read_Class_Static_Field, AccessTypes.write_Class_Static_Field)
                                        Modifier.isFinal(field.modifiers) -> setOf(AccessTypes.read_Class_Instance_Field)
                                        else -> setOf(AccessTypes.read_Class_Instance_Field, AccessTypes.write_Class_Instance_Field)
                                    }
                                    listOf(PolicyAllowance.ClassLevel.ClassFieldAccess(currentClassName!!, propertyName, propertySig, access))
                                } else {
                                    throw IllegalStateException("Property/Field not found! $currentClassName.$propertyName:$propertySig")
                                }
                            }
                        }
                    }

                }.flatten().filterNotNull().toList()
            }
        }.flatten().toList()
        return painlessPolicies
    }

    private fun loadClass(className: String): Class<*> {
        return when (className) {
            "boolean" -> Boolean::class.javaPrimitiveType!!
            "byte" -> Byte::class.javaPrimitiveType!!
            "short" -> Short::class.javaPrimitiveType!!
            "char" -> Char::class.javaPrimitiveType!!
            "int" -> Int::class.javaPrimitiveType!!
            "long" -> Long::class.javaPrimitiveType!!
            "float" -> Float::class.javaPrimitiveType!!
            "double" -> Double::class.javaPrimitiveType!!
            else -> Class.forName(className)
        }
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
}

