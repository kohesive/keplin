package uy.kohesive.cuarentena

import org.objectweb.asm.*
import org.objectweb.asm.signature.SignatureReader
import org.objectweb.asm.signature.SignatureVisitor
import uy.kohesive.cuarentena.policy.CuarentenaPolicyLoader


// TODO: this is from first round of exploring and work, needs overhauled to take those learnings into account, simplify
//       and develop the formal Kotlin whitelist, and then the lambda serializeable tiny whitelist.  More checking, more
//       eyes, continue to be more secure.

// TODO: replace all class, package, ... lists with Cuarentena policies

class ClassRestrictionVerifier(val otherAllowedClasses: Set<String>, val otherAllowedPackages: Set<String>) {

    companion object {
        // TODO: change to use cuarentena policy files, and have no link to painless dependencies
        val definitions = CuarentenaPolicyLoader.loadPolicy("painless-base-java")

        val kotinAllowedClasses = setOf("org.jetbrains.annotations.NotNull",
                "kotlin.jvm.internal.Intrinsics",
                "kotlin.jvm.internal.Lambda",
                "kotlin.Metadata",
                "kotlin.Unit",
                "kotlin.text.Regex",
                "kotlin.text.MatchResult",
                "kotlin.text.MatchGroupCollection",
                "kotlin.text.MatchGroup",
                "kotlin.TypeCastException",
                "kotlin.text.StringsKt",
                "org.jetbrains.annotations.Nullable",
                "[Ljava.lang.String;", // TODO: this needs other handling
                "java.util.regex.Pattern")  // TODO: we need a serializable white-list too


        val kotlinAllowedPackages = listOf("kotlin.collections.", "kotlin.jvm.functions.") // TODO: need specific list, these are not sealed

        private fun String.fixClassName() = this.replace('/', '.')
    }

    fun verifySafeClass(className: String, knownExtraAllowed: Set<String>, newClasses: List<NamedClassBytes>): VerifyResults {
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
                || checkClass in kotinAllowedClasses
                || checkClass in otherAllowedClasses
    }


    // TODO: change all this
    private fun findSymbol(rawName: String): String? {
        val name = rawName.fixClassName()
        return definitions.first()
    }

    val validTypes = ClassRestrictionVerifier.kotinAllowedClasses

    private fun isSpecialType(baseClass: String, containingClass: String, extraAllowed: Set<String>, rawName: String): Boolean {
        val type = rawName.fixClassName()
        return type in validTypes
                || type in extraAllowed
                || validClassFilter(baseClass, containingClass, type)
                || otherAllowedPackages.any { type.startsWith(it) }
                || kotlinAllowedPackages.any { type.startsWith(it) }
    }

    data class VerifyResults(val isScoreAccessed: Boolean, val violations: Set<String>) {
        val failed: Boolean = violations.isNotEmpty()
    }

    inner class VerifySafeClassVisitor(val myName: String, val extraAllowed: Set<String>) : ClassVisitor(Opcodes.ASM5) {
        val violations: MutableSet<String> = mutableSetOf()
        var scoreAccessed: Boolean = false

        val containingClass = myName.substringBefore("$")

        fun violation(rawName: String) {
            val type = rawName.fixClassName()
            if (type !in violations) {
                violations.add(type)
            }
        }

        fun assertType(rawName: String?) {
            if (rawName != null && !isSpecialType(myName, containingClass, extraAllowed, rawName) && findSymbol(rawName) == null) {
                violation(rawName)
            }
        }

        fun verify(note: String, owner: String, name: String? = null, desc: String? = null, sig: String? = null, vararg otherNames: String?) {
            if (otherNames.isNotEmpty()) {
                otherNames.filterNotNull().forEach { verify(note + "-on", it) }
            }

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

        inner class VerifyPartsOfSignature(val classVisitor: VerifySafeClassVisitor) : SignatureVisitor(Opcodes.ASM5) {
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

        override fun visitAnnotation(desc: String?, visible: Boolean): AnnotationVisitor? {
            verify("myAnn", myName, desc = desc)
            return VerifySafeAnnotationVisitor(this@VerifySafeClassVisitor, myName)
        }

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
            // TODO: should we scan the frame for extra safety?
        }

        override fun visitTypeInsn(opcode: Int, type: String) {
            verifyOther("mv.type", owner = type)
        }
        override fun visitAnnotationDefault(): AnnotationVisitor? {
            return VerifySafeAnnotationVisitor(classVisitor, methodName)
        }

        override fun visitAnnotation(desc: String?, visible: Boolean): AnnotationVisitor? {
            verify("mv.ann", desc = desc)
            return VerifySafeAnnotationVisitor(classVisitor, methodName)
        }
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
        }

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

        override fun visitParameter(name: String?, access: Int) {
            verify("mv.parm", name = name)
        }

        override fun visitMethodInsn(opcode: Int, owner: String, name: String?, desc: String?, itf: Boolean) {
            verifyOther("mv.methIns", owner = owner, name = name, desc = desc)

            if (owner == classVisitor.myName && "get_score" == name) {
                classVisitor.scoreAccessed = true
            }
        }

        override fun visitFieldInsn(op: Int, owner: String, name: String?, desc: String?) {
            verifyOther("mv.fieldIns", owner = owner, name = name, desc = desc)

            if (op == Opcodes.GETFIELD && name == "_score") {
                classVisitor.scoreAccessed = true
            }
        }
    }

}
