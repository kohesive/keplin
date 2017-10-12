package uy.kohesive.cuarentena.policy

import uy.kohesive.keplin.util.erasedType
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Type

fun Class<*>.safeName() = this.typeName
fun Type.safeName() = this.erasedType().typeName

fun Method.signature(): String {
    val checkParams = parameterTypes.map { typeToSigPart(it.safeName()) }
    val checkReturn = returnType.let { typeToSigPart(it.safeName()) }
    return "(${checkParams.joinToString("")})$checkReturn"
}

fun Constructor<*>.signature(): String {
    val checkParams = parameterTypes.map { typeToSigPart(it.safeName()) }
    val checkReturn = annotatedReturnType.type.let { typeToSigPart(it.safeName()) }
    return "(${checkParams.joinToString("")})$checkReturn"
}

fun Field.signature(): String {
    return type.let { uy.kohesive.cuarentena.policy.typeToSigPart(it.safeName()) }
}

fun typeToSigPart(type: String): String {
    val baseName = type.substringBefore('[')
    val suffix = type.removePrefix(baseName)

    val typeChar = when (baseName) {
        "void", java.lang.Void.TYPE.name, Unit::class.qualifiedName!! -> 'V'
        "boolean", Boolean::class.javaPrimitiveType!!.name, Boolean::class.qualifiedName!! -> 'Z'
        "byte", Byte::class.javaPrimitiveType!!.name -> 'B'
        "short", Short::class.javaPrimitiveType!!.name -> 'S'
        "char", Char::class.javaPrimitiveType!!.name -> 'C'
        "int", Int::class.javaPrimitiveType!!.name -> 'I'
        "long", Long::class.javaPrimitiveType!!.name -> 'J'
        "float", Float::class.javaPrimitiveType!!.name -> 'F'
        "double", Double::class.javaPrimitiveType!!.name -> 'D'
        else -> 'L'
    }

    val bracketCount = suffix.count { it == '[' }

    return "[".repeat(bracketCount) + typeChar + if (typeChar == 'L') baseName + ";" else ""
}
