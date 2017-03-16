package uy.kohesive.keplin.elasticsearch.kotlinscript

import org.elasticsearch.painless.Definition
import org.objectweb.asm.*
import org.objectweb.asm.signature.SignatureReader
import org.objectweb.asm.signature.SignatureVisitor

object ClassRestrictionVerifier {
    init {
        // make sure definitions are loaded for Painless whitelist
        Definition.getRuntimeClass(String::class.java)
    }

    val kotinAllowedClasses = setOf("org.jetbrains.annotations.NotNull",
            "kotlin.jvm.internal.Intrinsics",
            "kotlin.jvm.internal.Lambda",
            "kotlin.Metadata",
            "kotlin.Unit")

    val kotlinAllowedPackages = listOf("kotlin.collections.") // TODO: need specific list, these are not sealed

    fun verifySafeClass(className: String, knownExtraAllowed: Set<String>, newClasses: List<ByteArray>): VerifyResults {
        val readers = newClasses.map(::ClassReader)
        val verifier = VerifySafeClassVisitor(className, knownExtraAllowed)
        readers.forEach { reader ->
            reader.accept(verifier, 0)
        }
        return VerifyResults(verifier.scoreAccessed, verifier.violations)
    }

    data class VerifyResults(val isScoreAccessed: Boolean, val violations: Set<String>) {
        val failed: Boolean = violations.isNotEmpty()
    }

    class VerifySafeClassVisitor(val myName: String, val knownExtraAllowed: Set<String>) : ClassVisitor(Opcodes.ASM5) {
        val violations: MutableSet<String> = mutableSetOf()
        var scoreAccessed: Boolean = false

        val validTypes = setOf(myName) + ClassRestrictionVerifier.kotinAllowedClasses + knownExtraAllowed

        val classCache = mutableMapOf<String, Definition.RuntimeClass?>()
        val classMiss = mutableSetOf<String>()

        val containingClass = myName.substringBefore("$")

        fun validClassFilter(x: String): Boolean = x == myName || x.startsWith("$myName\$") || x == containingClass || x.startsWith("$containingClass\$") || x in ClassSerDesUtil.otherAllowedSerializedClasses

        fun String.fixClassName() = this.replace('/', '.')

        fun findClass(rawName: String): Definition.RuntimeClass? {
            val name = rawName.fixClassName()
            return if (name in classMiss) null
            else classCache.computeIfAbsent(name) {
                val result = try {
                    if (Definition.isSimpleType(name)) Definition.getRuntimeClass(Definition.getType(name).clazz)
                    else if (Definition.isType(name)) Definition.getRuntimeClass(Definition.getType(name).clazz)
                    else Definition.getRuntimeClass(Class.forName(it))
                } catch (ex: Exception) {
                    null
                }
                if (result == null) classMiss.add(name)
                result
            }
        }

        fun isSpecialType(rawName: String): Boolean {
            val type = rawName.fixClassName()
            return type in validTypes || validClassFilter(type)
                    || type.startsWith("${KotlinScriptPlugin::class.java.`package`.name}.")  // TODO specific list, or make sure we seal our package
                    || kotlinAllowedPackages.any { type.startsWith(it) }
        }

        fun violation(rawName: String) {
            val type = rawName.fixClassName()
            if (type !in violations) {
                println("**** VIOLATION: $type ****")
                violations.add(type)
            }
        }

        fun assertType(rawName: String?) {
            if (rawName != null && !isSpecialType(rawName) && findClass(rawName) == null) {
                println("Asserting: $rawName")
                violation(rawName)
            }
        }

        fun verify(note: String, owner: String, name: String? = null, desc: String? = null, sig: String? = null, vararg otherNames: String?) {
            // TODO: handle otherNames
            if (otherNames.isNotEmpty()) {
                println("asdf")
            }

            println("${note.padEnd(15)} :> owner: ${owner.padEnd(15)}  name: ${name?.padEnd(15)}  desc: ${desc?.padEnd(25)} sig: ${sig?.padEnd(25)}    otherNames: ${otherNames.filterNotNull().joinToString(",")}")
            if ('/' in owner && !isSpecialType(owner)) {
                println("  in owner check)")
                val ownerDef = findClass(owner)
                if (ownerDef == null) {
                    violation(owner)
                } else {
                    println("  have owner but not sure about method")
                    if (name != null) {
                        println("  have method name so checking method")
                        val methods = ownerDef.methods.map { it.value.method.name to it.value.method }
                        val getters = ownerDef.getters.map { it.key to it.value.type().toMethodDescriptorString() }
                        val setters = ownerDef.setters.map { it.key to it.value.type().toMethodDescriptorString() }

                        fun getterName(n: String): String = if (n.startsWith("get")) n.removePrefix("get") else if (n.startsWith("is")) n.removePrefix("is") else n
                        fun setterName(n: String): String = n.removePrefix("set")
                        if (methods.none { it.first == name && it.second.descriptor == desc } &&
                                getters.none { it.first == getterName(name) && it.second == desc } &&
                                setters.none { it.first == setterName(name) && it.second == desc }) {
                            violation("${owner}.${name}${desc}")
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
                    println("ooops")
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
}
