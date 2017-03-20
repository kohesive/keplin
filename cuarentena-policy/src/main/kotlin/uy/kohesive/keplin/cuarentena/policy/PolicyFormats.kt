package uy.kohesive.keplin.cuarentena.policy

data class DefClass(val painlessName: String, val javaName: String, val signatures: List<DefMethodSig>)
data class DefMethodSig(val returnType: String, val methodName: String, val paramTypes: List<String>, val isProperty: Boolean = false)
