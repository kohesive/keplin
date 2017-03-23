package uy.kohesive.cuarentena.policy


data class SignatureResult(val method: Boolean, val parameters: List<String>, val parameterShorts: List<String>, val returnType: String, val returnTypeShort: String)


// from things like "boolean, my.pack.Class, int[], String[][], void" to things like "Z, Lmy.pack.Class;, [I, [[Ljava.lang.String, V"
//
// Here we collapse all signatures down so that any object/primitive types end up as primitives to match Kotlin which
// auto adjusts for the right case.

fun typeToNormalizedSigPart(type: String): String {
    val baseName = type.substringBefore('[')
    val suffix = type.removePrefix(baseName)

    val typeChar = when (baseName) {
        "void", java.lang.Void.TYPE.name, Unit::class.qualifiedName!! -> 'V'
        "boolean", Boolean::class.javaPrimitiveType!!.name, Boolean::class.javaObjectType.name -> 'Z'
        "byte", Byte::class.javaPrimitiveType!!.name, Byte::class.javaObjectType.name -> 'B'
        "short", Short::class.javaPrimitiveType!!.name, Short::class.javaObjectType.name -> 'S'
        "char", Char::class.javaPrimitiveType!!.name, Char::class.javaObjectType.name -> 'C'
        "int", Int::class.javaPrimitiveType!!.name, Int::class.javaObjectType.name -> 'I'
        "long", Long::class.javaPrimitiveType!!.name, Long::class.javaObjectType.name -> 'J'
        "float", Float::class.javaPrimitiveType!!.name, Float::class.javaObjectType.name -> 'F'
        "double", Double::class.javaPrimitiveType!!.name, Double::class.javaObjectType.name -> 'D'
        else -> 'L'
    }

    val bracketCount = suffix.count { it == '[' }

    return "[".repeat(bracketCount) + typeChar + if (typeChar == 'L') baseName + ";" else ""
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

/*
fun javaOnlyTypeToSigPart(type: String): String {
    val remap = when (type) {
        kotlin.Any::class.qualifiedName -> "java.lang.Object"
        "kotlin.Int" -> "int"
        "kotlin.Long" -> "long"
        "kotlin.Double" -> "double"
        "kotlin.Char" -> "char"
        "kotlin.Float" -> "float"
        "kotlin.Short" -> "short"
        "kotlin.Byte" -> "byte"
        "kotlin.Boolean" -> "boolean"
        "kotlin.String" -> "java.lang.String"
        "kotlin.collections.List" -> "java.util.List"
        else -> type
    }
    return typeToNormalizedSigPart(remap)
}
        */