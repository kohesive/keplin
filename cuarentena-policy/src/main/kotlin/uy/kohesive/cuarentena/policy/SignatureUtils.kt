package uy.kohesive.cuarentena.policy

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
