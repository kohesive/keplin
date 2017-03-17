package uy.kohesive.keplin.elasticsearch.kotlinscript

import org.elasticsearch.painless.Definition
import org.objectweb.asm.*
import org.objectweb.asm.signature.SignatureReader
import org.objectweb.asm.signature.SignatureVisitor

object ClassRestrictionVerifier {
    val kotinAllowedClasses = setOf("org.jetbrains.annotations.NotNull",
            "kotlin.jvm.internal.Intrinsics",
            "kotlin.jvm.internal.Lambda",
            "kotlin.Metadata",
            "kotlin.Unit",
            "org.jetbrains.annotations.Nullable")


    val extraAllowedSymbols = listOf(DefClass("StringBuilder", "java.lang.StringBuilder", listOf(
            DefMethodSig("java.lang.StringBuilder", "append", listOf("java.lang.String")),
            DefMethodSig("java.lang.StringBuilder", "append", listOf("int")),
            DefMethodSig("java.lang.StringBuilder", "append", listOf("long")),
            DefMethodSig("java.lang.StringBuilder", "append", listOf("char")),
            DefMethodSig("java.lang.StringBuilder", "append", listOf("double")),
            DefMethodSig("java.lang.StringBuilder", "append", listOf("float")),
            DefMethodSig("java.lang.StringBuilder", "append", listOf("byte")),
            DefMethodSig("java.lang.StringBuilder", "append", listOf("short")),
            DefMethodSig("java.lang.StringBuilder", "append", listOf("boolean")),
            DefMethodSig("java.lang.String", "toString", emptyList()))))

    val kotlinAllowedPackages = listOf("kotlin.collections.") // TODO: need specific list, these are not sealed

    fun verifySafeClass(className: String, knownExtraAllowed: Set<String>, newClasses: List<KotlinScriptEngineService.NamedClassBytes>): VerifyResults {
        val readers = newClasses.map { ClassReader(it.bytes) }
        val verifier = VerifySafeClassVisitor(className, knownExtraAllowed)
        readers.forEach { reader ->
            reader.accept(verifier, 0)
        }
        return VerifyResults(verifier.scoreAccessed, verifier.violations)
    }

    // TODO: probably a smaller list for deserializing an instance, because not much is really needed
    fun verifySafeClassForDeser(mainClassName: String, knownExtraAllowed: Set<String>, checkClassName: String): VerifyResults {
        val className = mainClassName.fixClassName()
        val checkName = checkClassName.fixClassName()
        val allowed = knownExtraAllowed.map { it.fixClassName() }.toSet()
        val containingClass = className.substringBefore("$")

        if ('.' in checkName && !isSpecialType(className, containingClass, knownExtraAllowed, checkName) && checkName !in allowed) {
            if (findSymbol(checkName) == null) {
                return VerifyResults(false, setOf(checkName))
            }
        }
        return VerifyResults(false, emptySet())
    }

    private fun validClassFilter(baseClass: String, containingClass: String, checkClass: String): Boolean {
        return checkClass == baseClass
                || checkClass.startsWith("$baseClass\$")
                || checkClass == containingClass
                || checkClass.startsWith("$containingClass\$")
                || checkClass in ClassSerDesUtil.otherAllowedSerializedClasses
    }

    private fun String.fixClassName() = this.replace('/', '.')

    private fun findSymbol(rawName: String): DefClass? {
        val name = rawName.fixClassName()
        return definitions[name]
    }

    val validTypes = ClassRestrictionVerifier.kotinAllowedClasses

    private fun isSpecialType(baseClass: String, containingClass: String, extraAllowed: Set<String>, rawName: String): Boolean {
        val type = rawName.fixClassName()
        return type in validTypes
                || type in extraAllowed
                || validClassFilter(baseClass, containingClass, type)
                || type.startsWith("${KotlinScriptPlugin::class.java.`package`.name}.")  // TODO specific list, or make sure we seal our package
                || kotlinAllowedPackages.any { type.startsWith(it) }
    }

    data class VerifyResults(val isScoreAccessed: Boolean, val violations: Set<String>) {
        val failed: Boolean = violations.isNotEmpty()
    }

    class VerifySafeClassVisitor(val myName: String, val extraAllowed: Set<String>) : ClassVisitor(Opcodes.ASM5) {
        val violations: MutableSet<String> = mutableSetOf()
        var scoreAccessed: Boolean = false

        val containingClass = myName.substringBefore("$")

        fun violation(rawName: String) {
            val type = rawName.fixClassName()
            if (type !in violations) {
                // println("**** VIOLATION: $type ****")
                violations.add(type)
            }
        }

        fun assertType(rawName: String?) {
            if (rawName != null && !isSpecialType(myName, containingClass, extraAllowed, rawName) && findSymbol(rawName) == null) {
                //  println("Asserting: $rawName")
                violation(rawName)
            }
        }

        fun verify(note: String, owner: String, name: String? = null, desc: String? = null, sig: String? = null, vararg otherNames: String?) {
            if (otherNames.isNotEmpty()) {
                otherNames.filterNotNull().forEach { verify(note + "-on", it) }
            }

            //  println("${note.padEnd(15)} :> owner: ${owner.padEnd(15)}  name: ${name?.padEnd(15)}  desc: ${desc?.padEnd(25)} sig: ${sig?.padEnd(25)}    otherNames: ${otherNames.filterNotNull().joinToString(",")}")
            if ('/' in owner && !isSpecialType(myName, containingClass, extraAllowed, owner)) {
                val ownerDef = findSymbol(owner)
                if (ownerDef == null) {
                    violation(owner)
                } else {
                    if (name != null) {
                        val className = owner.fixClassName()
                        val methodSig = "$className.$name$desc"
                        val methodDef = findSymbol(methodSig)
                        if (methodDef == null) {
                            violation(methodSig)
                        }
                    }
                }
            }

            val sigReaders = listOf(desc, sig).filterNotNull().map {
                SignatureReader(it)
            }

            val visitor = VerifyPartsOfSignature(this)
            sigReaders.forEach {
                try {
                    it.accept(visitor)
                } catch (ex: Exception) {
                    throw ex // TODO: not really much we can do, maybe better error report
                }
            }
        }

        class VerifyPartsOfSignature(val classVisitor: VerifySafeClassVisitor) : SignatureVisitor(Opcodes.ASM5) {
            fun assertType(type: String?) = classVisitor.assertType(type)

            override fun visitParameterType(): SignatureVisitor {
                return this
            }

            override fun visitFormalTypeParameter(name: String?) {
                assertType(name)
            }

            override fun visitClassBound(): SignatureVisitor {
                return this
            }

            override fun visitInterface(): SignatureVisitor {
                return this
            }

            override fun visitTypeVariable(name: String?) {
                assertType(name)
            }

            override fun visitExceptionType(): SignatureVisitor {
                return this
            }

            override fun visitInterfaceBound(): SignatureVisitor {
                return this
            }

            override fun visitArrayType(): SignatureVisitor {
                return this
            }

            override fun visitSuperclass(): SignatureVisitor {
                return this
            }

            override fun visitInnerClassType(name: String?) {
                assertType(name)
            }

            override fun visitReturnType(): SignatureVisitor {
                return this
            }

            override fun visitClassType(name: String?) {
                assertType(name)
            }
        }

        override fun visitMethod(access: Int, name: String, desc: String?, signature: String?, exceptions: Array<out String>?): MethodVisitor? {
            verify("method", myName, name, desc = desc, sig = signature, otherNames = *exceptions ?: emptyArray())
            return VerifySafeMethodVisitor(this, "${myName}.${name}")
        }
// desc = ()Ljava/io/File; name = getX   (now ()Ljava/util/List; sig ()Ljava/util/List<Ljava/io/File;>;)
// desc = (Ljava/io/File;)Z ; name = foo  /// name = getZ ... desc ()Ljava/util/List; sig ()Ljava/util/List<Ljava/lang/String;>; // name = getQ desc = ()[Ljava/lang/Integer; // name = getS desc = ()Ljava/lang/String;
// desc = (Ljava/util/Map;Ljava/util/Map;Ljava/util/Map;Ljava/lang/Object;D)V  name = <init>

        override fun visitInnerClass(name: String, outerName: String?, innerName: String?, access: Int) {
            verify("innerClass", myName, name, otherNames = *arrayOf(innerName, outerName))
        }

        override fun visitOuterClass(owner: String, name: String?, desc: String?) {
            verify("outerClass", owner = owner, name = name, desc = desc, otherNames = owner)
        }

        override fun visitField(access: Int, name: String, desc: String?, signature: String?, value: Any?): FieldVisitor? {
            verify("myField", myName, name, desc = desc, sig = signature)
            return object : FieldVisitor(Opcodes.ASM5) {
                override fun visitAnnotation(desc: String?, visible: Boolean): AnnotationVisitor {
                    verify("mf.ann", myName, name, desc = desc)
                    return VerifySafeAnnotationVisitor(this@VerifySafeClassVisitor, name)
                }

                override fun visitTypeAnnotation(typeRef: Int, typePath: TypePath?, desc: String?, visible: Boolean): AnnotationVisitor {
                    verify("mf.typeAnn", myName, name, desc = desc, sig = typePath?.toString())
                    return VerifySafeAnnotationVisitor(this@VerifySafeClassVisitor, name)
                }

            }
        }
// desc Ljava/io/File;  name x      (now:  desc Ljava/util/List; sig Ljava/util/List<Ljava/io/File;>;)
// desc Ljava/lang/Object; name $$result   /// name z desc Ljava/util/List; sig Ljava/util/List<Ljava/lang/String;>; /// name q desc [Ljava/lang/Integer; sign null /// name s desc Ljava/lang/String; sig null /// $$result desc Ljava/lang/Object; sig null

        override fun visitAnnotation(desc: String?, visible: Boolean): AnnotationVisitor? {
            verify("myAnn", myName, desc = desc)
            return VerifySafeAnnotationVisitor(this@VerifySafeClassVisitor, myName)
        }
// desc = Lkotlin/Metadata;

        override fun visitTypeAnnotation(typeRef: Int, typePath: TypePath?, desc: String?, visible: Boolean): AnnotationVisitor? {
            verify("myTypeAnn", myName, desc = desc, sig = typePath?.toString())
            return VerifySafeAnnotationVisitor(this, myName)
        }

    }

    class VerifySafeAnnotationVisitor(val classVisitor: VerifySafeClassVisitor, val annName: String) : AnnotationVisitor(Opcodes.ASM5) {
        fun verify(note: String, name: String? = null, desc: String? = null, sig: String? = null, vararg otherNames: String?) {
            classVisitor.verify(note, annName, name, desc, sig, *otherNames)
        }

        override fun visitAnnotation(name: String, desc: String?): AnnotationVisitor {
            verify("ann.va", name = name, desc = desc)
            return VerifySafeAnnotationVisitor(classVisitor, name)
        }

        override fun visitEnum(name: String?, desc: String?, value: String?) {
            verify("ann.ve", name = name, desc = desc)
        }

        override fun visit(name: String?, value: Any?) {
            // verify("ann.vi", name = name)
        }

        override fun visitArray(name: String): AnnotationVisitor {
            verify("ann.varr", name = name)
            return VerifySafeAnnotationVisitor(classVisitor, name)
        }

    }

    class VerifySafeMethodVisitor(val classVisitor: VerifySafeClassVisitor, val methodName: String) : MethodVisitor(Opcodes.ASM5) {
        fun verify(note: String, name: String? = null, desc: String? = null, sig: String? = null, vararg otherNames: String?) {
            classVisitor.verify(note, methodName + "()", name, desc, sig, *otherNames)
        }

        fun verifyOther(note: String, owner: String, name: String? = null, desc: String? = null, sig: String? = null, vararg otherNames: String?) {
            classVisitor.verify(note, owner, name, desc, sig, *otherNames)
        }

        override fun visitMultiANewArrayInsn(desc: String?, dims: Int) {
            verify("mv.newArr", desc = desc)
        }

        override fun visitFrame(type: Int, nLocal: Int, local: Array<out Any>?, nStack: Int, stack: Array<out Any>?) {

        } // should we scan the local or stack for objects we don't like as a backup?


        override fun visitTypeInsn(opcode: Int, type: String) {
            verifyOther("mv.type", owner = type)
        } // java/io/File  opcode 187 = new  // java/util/ArrayList opcode 187 = new // java/lang/String 189 = newArray // Line_1  opcode 192 = checkCast  // java/lang/Iterable opcode 192 = checkCast // java.io.File opcode 192 // java/util/collection 192  // java/util/list 192

        override fun visitAnnotationDefault(): AnnotationVisitor? {
            return VerifySafeAnnotationVisitor(classVisitor, methodName)
        }

        override fun visitAnnotation(desc: String?, visible: Boolean): AnnotationVisitor? {
            verify("mv.ann", desc = desc)
            return VerifySafeAnnotationVisitor(classVisitor, methodName)
        } // Lorg/jetbrains/annotations/NotNull;

        override fun visitTypeAnnotation(typeRef: Int, typePath: TypePath?, desc: String?, visible: Boolean): AnnotationVisitor? {
            verify("mv.typeAnn", desc = desc, sig = typePath?.toString())
            return VerifySafeAnnotationVisitor(classVisitor, methodName)
        }

        override fun visitTryCatchAnnotation(typeRef: Int, typePath: TypePath?, desc: String?, visible: Boolean): AnnotationVisitor? {
            verify("mv.tryAnn", desc = desc, sig = typePath?.toString())
            return VerifySafeAnnotationVisitor(classVisitor, methodName)
        }

        override fun visitInsnAnnotation(typeRef: Int, typePath: TypePath?, desc: String?, visible: Boolean): AnnotationVisitor? {
            verify("mv.insAnn", desc = desc, sig = typePath?.toString())
            return VerifySafeAnnotationVisitor(classVisitor, methodName)
        }

        override fun visitParameterAnnotation(parameter: Int, desc: String?, visible: Boolean): AnnotationVisitor? {
            verify("mv.parAnn", desc = desc)
            return VerifySafeAnnotationVisitor(classVisitor, methodName)
        } // Lorg/jetbrains/annotations/NotNull;

        override fun visitInvokeDynamicInsn(name: String?, desc: String?, bsm: Handle?, vararg bsmArgs: Any?) {
            verify("mv.invDyn", name = name, desc = desc)
        }

        override fun visitLocalVariableAnnotation(typeRef: Int, typePath: TypePath?, start: Array<out Label>?, end: Array<out Label>?, index: IntArray?, desc: String?, visible: Boolean): AnnotationVisitor? {
            verify("mv.locVarAnn", desc = desc, sig = typePath?.toString())
            return VerifySafeAnnotationVisitor(classVisitor, methodName)
        }

        override fun visitLocalVariable(name: String?, desc: String?, signature: String?, start: Label?, end: Label?, index: Int) {
            verify("mv.local", name = name, desc = desc, sig = signature)
        }
        // name = "this" ... desc = "LLine_1;"
        // name = "y" ... desc = "Ljava/io/File;"
        // name it Ljava/io/File;
        // name $i$a$1$forEach desc I
        // name element$iv desc Ljava/lang/Object;
        // name $receiver$iv desc Ljava/lang/Iterable;
        // name $i$f$forEach desc I

        override fun visitParameter(name: String?, access: Int) {
            verify("mv.parm", name = name)
        }

        override fun visitMethodInsn(opcode: Int, owner: String, name: String?, desc: String?, itf: Boolean) {
            verifyOther("mv.methIns", owner = owner, name = name, desc = desc)

            if (owner == classVisitor.myName && "get_score" == name) {
                classVisitor.scoreAccessed = true
            }
        }
        // owner = kotlin/jvm/internal/Intrinsics | name = checkParameterIsNotNull | desc = (Ljava/lang/Object;Ljava/lang/String;)V  ...
        // owner = com/bremeld/es/topology/planner/plugin/EsKotlinScriptTemplate | name = "<init>" | value = "(Ljava/util/Map;Ljava/util/Map;Ljava/util/Map;Ljava/lang/Object;D)V"
        // owner = java/io/File | name = exists | desc = ()Z
        // owner = java/io/PrintStream  | name = println | desc = (Ljava/lang/Object;)V /// owner = java/util/Collection | name = "add" | desc = (Ljava/lang/Object;)Z
        // owner = java/io/File | name = "<init>" | desc = (Ljava/lang/String;)V  /// owner = java/lang/Boolean | name = valueOf | desc = (Z)Ljava/lang/Boolean;
        // owner = Line_1 | name = docInt | desc = (Ljava/lang/String;I)I  /// owner = java/util/ArrayList | name = "<init>" | desc = (I)V
        // owner = Line_1 | name = "parmInt" | desc = (Ljava/lang/String;I)I /// owner = kotlin/collections/CollectionsKt | name = collectionSizeOrDefault | desc = (Ljava/lang/Iterable;I)I
        // owner = Line_1 name = "get_score" desc = "()D"    /// owner = java/util/Iterator | name = next | desc = ()Ljava/lang/Object;
        // owner = "java/lang/Double" name = "valueOf" desc = "(D)Ljava/lang/Double;" /// onwer = kotlin/collections/CollectionsKt | name = listOf | desc = (Ljava/lang/Object;)Ljava/util/List; /// owner = java/lang/Iterable | name = iterator | desc = ()Ljava/util/Iterator; /// owner = java/util/Iterator | name = hasNext | desc = ()Z

        override fun visitFieldInsn(op: Int, owner: String, name: String?, desc: String?) {
            verifyOther("mv.fieldIns", owner = owner, name = name, desc = desc)

            if (op == Opcodes.GETFIELD && name == "_score") {
                classVisitor.scoreAccessed = true
            }
            // owner = "Line_1" ... name = "x"  ... desc = "Ljava/io/File;"  (now desc = Ljava/util/List;)
            // owner = "java/lang/System" name = "out" desc = "Ljava/io/PrintStream;"  /// owner Line_1 name z desc Ljava/util/List; /// owner Line_1 name = q desc = [Ljava/lang/Integer;
            // owner = "Line_1" name = "$$result" desc = "Ljava/lang/Object;"
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

    data class DefClass(val painlessName: String, val javaName: String, val signatures: List<DefMethodSig>)
    data class DefMethodSig(val returnType: String, val methodName: String, val paramTypes: List<String>, val isProperty: Boolean = false)

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

    // read painless definitions and map things by java names
    //      fullClassName
    //      fullClassName.<init>(sig)
    //      fullClassName.foo(sig)
    //      fullClassName.getVar()Lwhatever;
    //
    val definitions = readDefinitions()

    private fun readDefinitions(): Map<String, DefClass> {
        val classSigRegex = """^class\s+([\w\_][\w\_\d\.\$]*)\s+\-\>\s+([\w\_][\w\_\d\.\$]*)(?:\s+extend[s]?\s+([\w\_][\w\_\d\.\,\$]*)\s*)?\s*\{$""".toRegex()
        val methodPartsRegex = """^([\w\_][\w\_\d\.]*(?:\[\])*)\s+((?:[\w\_][\w\_\d\.]*|\<init\>)\*?)\(([\w\_][\w\_\d\.\,\[\]]*)?\)[;]?$""".toRegex()
        val propertyPartsRegex = """^([\w\_][\w\_\d\.]*(?:\[\])*)\s+([\w\_][\w\_\d\.]*\*?)$""".toRegex()

        val definitionResources = DEFINITION_FILES.map { Definition::class.java.getResourceAsStream(it) }
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
}
