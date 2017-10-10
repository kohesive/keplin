package uy.kohesive.cuarentena

import org.objectweb.asm.*
import org.objectweb.asm.signature.SignatureReader
import org.objectweb.asm.signature.SignatureVisitor
import uy.kohesive.cuarentena.policy.AccessTypes
import uy.kohesive.cuarentena.policy.PolicyAllowance

object ClassAllowanceDetector {

    fun scanClassByteCodeForDesiredAllowances(classNamesWithBytes: List<NamedClassBytes>): ScanState {
        val collected = ScanState()
        classNamesWithBytes.forEach { ClassReader(it.bytes).accept(ClassAllowanceScanner(it.className, collected), 0) }
        return collected
    }

    class ScanState(val allowances: MutableList<PolicyAllowance.ClassLevel> = mutableListOf(),
                    val createsMethods: MutableList<CreatedClassMethod> = mutableListOf(),
                    val createsClass: MutableList<CreatedClass> = mutableListOf(),
                    val createsFields: MutableList<CreatedClassField> = mutableListOf())

    private fun ScanState.requestClassRef(refClass: String) {
        allowances.add(PolicyAllowance.ClassLevel.ClassAccess(refClass, setOf(AccessTypes.ref_Class)))
    }

    private fun ScanState.requestClassInstanceRef(refClass: String) {
        allowances.add(PolicyAllowance.ClassLevel.ClassAccess(refClass, setOf(AccessTypes.ref_Class_Instance)))
    }

    private fun ScanState.requestsFromDescSig(desc: String?, signature: String?) {
        // desc is sig without generics
        val sig = signature ?: desc ?: run {
            throw IllegalArgumentException("Need either a descriptor or signature")
        }
        val sigReader = SignatureReader(sig)
        sigReader.accept(SignatureAllowanceScanner(this))
    }


    private fun ScanState.createClassMethod(accessFlags: Int, className: String, methodName: String, desc: String?, signature: String?, exceptions: List<String>) {
        createsMethods.add(CreatedClassMethod(accessFlags, className, methodName, desc, signature, exceptions))
        exceptions.forEach { requestClassRef(it) }
        requestsFromDescSig(desc, signature)
    }

    private fun ScanState.createClassField(accessFlags: Int, className: String, fieldName: String, desc: String?, signature: String?, value: Any?) {
        createsFields.add(CreatedClassField(accessFlags, className, fieldName, desc, signature, value))
        requestsFromDescSig(desc, signature)
    }


    private fun ScanState.createClass(accessFlags: Int, className: String, signature: String?, superName: String?, interfaces: List<String> = emptyList()) {
        createsClass.add(CreatedClass(accessFlags, className, signature, superName, interfaces))
        superName?.let { requestClassInstanceRef(it) }
        interfaces.forEach { requestClassRef(it) }
        signature?.let { requestsFromDescSig(null, it) }
    }

    data class CreatedClassMethod(val accessFlags: Int, val className: String, val methodName: String, val desc: String?, val signature: String?, val exceptions: List<String>) {
        val isStatic: Boolean = accessFlags.and(Opcodes.ACC_STATIC) != 0
    }

    data class CreatedClass(val accessFlags: Int, val className: String, val signature: String?, val superName: String?, val interfaces: List<String> = emptyList()) {

    }

    data class CreatedClassField(val accessFlags: Int, val className: String, val fieldName: String, val desc: String?, val signature: String?, val value: Any?) {
        val isStatic: Boolean = accessFlags.and(Opcodes.ACC_STATIC) != 0
    }

    class ClassAllowanceScanner(val myClassName: String, val collect: ScanState) : ClassVisitor(Opcodes.ASM5) {
        override fun visit(version: Int, access: Int, name: String, signature: String?, superName: String?, interfaces: Array<String>?) {
            collect.createClass(access, name, signature, superName, interfaces?.toList() ?: emptyList())
        }

        override fun visitMethod(access: Int, name: String, desc: String?, signature: String?, exceptions: Array<out String>?): MethodVisitor? {
            collect.createClassMethod(access, myClassName, name, desc, signature, exceptions?.toList() ?: emptyList())
            return MethodAllowanceScanner(myClassName, name, collect)
        }

        override fun visitInnerClass(name: String, outerName: String?, innerName: String?, access: Int) {
            // TODO: this likely isn't important, we catch inner classes on "create class", do we need a reference as well?
        }

        override fun visitOuterClass(owner: String, name: String?, desc: String?) {
            // TODO:  this might not be important if not referenced or don't need to serialize it with the lambda
        }

        override fun visitField(access: Int, name: String, desc: String?, signature: String?, value: Any?): FieldVisitor? {
            collect.createClassField(access, myClassName, name, desc, signature, value)

            return object : FieldVisitor(Opcodes.ASM5) {
                override fun visitAnnotation(desc: String?, visible: Boolean): AnnotationVisitor {
                    collect.requestsFromDescSig(desc, null)
                    return AnnotationAllowanceScanner(collect)
                }

                override fun visitTypeAnnotation(typeRef: Int, typePath: TypePath?, desc: String?, visible: Boolean): AnnotationVisitor {
                    collect.requestsFromDescSig(desc, typePath?.toString())
                    return AnnotationAllowanceScanner(collect)
                }

            }
        }

        override fun visitAnnotation(desc: String?, visible: Boolean): AnnotationVisitor? {
            collect.requestsFromDescSig(desc, null)
            return AnnotationAllowanceScanner(collect)
        }

        override fun visitTypeAnnotation(typeRef: Int, typePath: TypePath?, desc: String?, visible: Boolean): AnnotationVisitor? {
            collect.requestsFromDescSig(desc, typePath?.toString())
            return AnnotationAllowanceScanner(collect)
        }

    }

    class MethodAllowanceScanner(val myClassName: String, val methodName: String, val collect: ScanState) : MethodVisitor(Opcodes.ASM5) {
        override fun visitMultiANewArrayInsn(desc: String?, dims: Int) {
            collect.requestsFromDescSig(desc, null)
        }

        override fun visitFrame(type: Int, nLocal: Int, local: Array<out Any>?, nStack: Int, stack: Array<out Any>?) {
            // TODO: should we scan the frame for extra safety?
        }

        override fun visitTypeInsn(opcode: Int, type: String) {
            when (opcode) {
                Opcodes.NEW -> {
                    collect.allowances.add(PolicyAllowance.ClassLevel.ClassAccess(type, setOf(AccessTypes.ref_Class_Instance)))
                }
                Opcodes.ANEWARRAY -> {
                    collect.allowances.add(PolicyAllowance.ClassLevel.ClassAccess(type, setOf(AccessTypes.ref_Class_Instance)))
                }
                Opcodes.CHECKCAST, Opcodes.INSTANCEOF -> {
                    collect.allowances.add(PolicyAllowance.ClassLevel.ClassAccess(type, setOf(AccessTypes.ref_Class_Instance)))
                }
                else -> throw IllegalStateException("Unknown opcode for method.visitTypeInsn: $opcode")
            }
        }

        override fun visitAnnotationDefault(): AnnotationVisitor? {
            return AnnotationAllowanceScanner(collect)
        }

        override fun visitAnnotation(desc: String?, visible: Boolean): AnnotationVisitor? {
            collect.requestsFromDescSig(desc, null)
            return AnnotationAllowanceScanner(collect)
        }

        override fun visitTypeAnnotation(typeRef: Int, typePath: TypePath?, desc: String?, visible: Boolean): AnnotationVisitor? {
            collect.requestsFromDescSig(desc, typePath?.toString())
            return AnnotationAllowanceScanner(collect)
        }

        override fun visitTryCatchAnnotation(typeRef: Int, typePath: TypePath?, desc: String?, visible: Boolean): AnnotationVisitor? {
            // TODO: check typeRef types if we want to do anything special
            collect.requestsFromDescSig(desc, typePath?.toString())
            return AnnotationAllowanceScanner(collect)
        }

        override fun visitInsnAnnotation(typeRef: Int, typePath: TypePath?, desc: String?, visible: Boolean): AnnotationVisitor? {
            // TODO: check typeRef types if we want to do anything special
            collect.requestsFromDescSig(desc, typePath?.toString())
            return AnnotationAllowanceScanner(collect)
        }

        override fun visitParameterAnnotation(parameter: Int, desc: String?, visible: Boolean): AnnotationVisitor? {
            collect.requestsFromDescSig(desc, null)
            return AnnotationAllowanceScanner(collect)
        }

        override fun visitInvokeDynamicInsn(name: String?, desc: String?, bsm: Handle?, vararg bsmArgs: Any?) {
            TODO("for what class is it invoking dynamic?")
        }

        override fun visitLocalVariableAnnotation(typeRef: Int, typePath: TypePath?, start: Array<out Label>?, end: Array<out Label>?, index: IntArray?, desc: String?, visible: Boolean): AnnotationVisitor? {
            collect.requestsFromDescSig(desc, typePath?.toString())
            return AnnotationAllowanceScanner(collect)
        }

        override fun visitLocalVariable(name: String?, desc: String?, signature: String?, start: Label?, end: Label?, index: Int) {
            collect.requestsFromDescSig(desc, signature)
        }

        override fun visitParameter(name: String?, access: Int) {
            // noop
        }

        override fun visitMethodInsn(opcode: Int, owner: String, name: String, desc: String, itf: Boolean) {
            // opcode is either INVOKEVIRTUAL, INVOKESPECIAL, INVOKESTATIC or INVOKEINTERFACE

            if (name == "<init>") {
                if (opcode == Opcodes.INVOKESPECIAL) {
                    collect.allowances.add(PolicyAllowance.ClassLevel.ClassConstructorAccess(owner, desc, setOf(AccessTypes.call_Class_Constructor)))
                } else {
                    throw IllegalStateException("Invalid op code for visitMethodInsn <init>: $opcode")
                }
            } else {
                val access = when (opcode) {
                    Opcodes.INVOKESPECIAL,
                    Opcodes.INVOKEVIRTUAL,
                    Opcodes.INVOKEINTERFACE -> AccessTypes.call_Class_Instance_Method
                    Opcodes.INVOKESTATIC -> AccessTypes.call_Class_Static_Method
                    else -> throw IllegalStateException("Invalid op code for visitMethodInsn: $opcode, name=$name")
                }
                collect.allowances.add(PolicyAllowance.ClassLevel.ClassMethodAccess(owner, name, desc, setOf(access)))
            }
            collect.requestsFromDescSig(desc, null)
        }

        override fun visitFieldInsn(opcode: Int, owner: String, name: String, desc: String) {
            // opcode is either GETSTATIC, PUTSTATIC, GETFIELD or PUTFIELD
            val access = when (opcode) {
                Opcodes.GETFIELD -> AccessTypes.read_Class_Instance_Field
                Opcodes.PUTFIELD -> AccessTypes.write_Class_Instance_Field
                Opcodes.GETSTATIC -> AccessTypes.read_Class_Static_Field
                Opcodes.PUTSTATIC -> AccessTypes.write_Class_Static_Field
                else -> throw IllegalStateException("Invalid op code for visitFieldInsn: $opcode")
            }
            collect.allowances.add(PolicyAllowance.ClassLevel.ClassFieldAccess(owner, name, desc, setOf(access)))
            collect.requestsFromDescSig(desc, null)
        }
    }

    class AnnotationAllowanceScanner(val collect: ScanState) : AnnotationVisitor(Opcodes.ASM5) {
        override fun visitAnnotation(name: String?, desc: String?): AnnotationVisitor {
            collect.requestsFromDescSig(desc, null)
            return AnnotationAllowanceScanner(collect)
        }

        override fun visitEnum(name: String?, desc: String?, value: String?) {
//            TODO("what about enum")
        }

        override fun visit(name: String?, value: Any?) {
            // primitive value
        }

        override fun visitArray(name: String?): AnnotationVisitor {
            return AnnotationAllowanceScanner(collect)
        }

    }


    class SignatureAllowanceScanner(val collect: ScanState) : SignatureVisitor(Opcodes.ASM5) {
        override fun visitParameterType(): SignatureVisitor {
            return this
        }

        override fun visitFormalTypeParameter(name: String) {
//            collect.allowances.add(PolicyAllowance.ClassLevel.ClassAccess(name.replace('/', '.'), setOf(AccessTypes.ref_Class)))
        }

        override fun visitTypeVariable(name: String) {
//            collect.allowances.add(PolicyAllowance.ClassLevel.ClassAccess(name.replace('/', '.'), setOf(AccessTypes.ref_Class)))
        }

        override fun visitInnerClassType(name: String) {
            collect.allowances.add(PolicyAllowance.ClassLevel.ClassAccess(name.replace('/', '.'), setOf(AccessTypes.ref_Class)))
        }

        override fun visitClassType(name: String) {
            collect.allowances.add(PolicyAllowance.ClassLevel.ClassAccess(name.replace('/', '.'), setOf(AccessTypes.ref_Class)))
        }

        override fun visitClassBound(): SignatureVisitor {
            return this
        }

        override fun visitInterface(): SignatureVisitor {
            return this
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

        override fun visitReturnType(): SignatureVisitor {
            return this
        }
    }

}

