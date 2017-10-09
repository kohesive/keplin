package uy.kohesive.cuarentena.policy

typealias AccessPolicies = List<PolicyAllowance>

sealed class PolicyAllowance(_fqnTarget: String, val actions: Set<AccessTypes>, val validActions: Set<AccessTypes>) {

    companion object {
        val ArrayInSigRegexp = "\\[L(.*);".toRegex()
        fun String.replaceArrayWithTypeParameter() = replace(ArrayInSigRegexp) { it.groupValues[1] }
    }

    val fqnTarget: String = _fqnTarget.replaceArrayWithTypeParameter().replace('/', '.')

    init {
        if (actions.any { it !in validActions }) {
            throw IllegalArgumentException("Requested actions $actions are not valid for ${this::class.qualifiedName!!} ($validActions)")
        }
    }

    abstract fun asPolicyStrings(): List<String>
    abstract fun asCheckStrings(explode: Boolean): List<String>

    class PackageAccess(fqPackageName: String, actions: Set<AccessTypes>, val requireSealed: Boolean = true) : PolicyAllowance(fqPackageName, actions, ALL_PACKAGE_ACCESS_TYPES) {
        val packageId = fqnTarget + if (requireSealed) ":sealed" else ""

        override fun asPolicyStrings(): List<String> {
            return actions.addDefaultClassActions().asStrings().map { "$packageId * $it" }
        }

        override fun asCheckStrings(explode: Boolean): List<String> = actions.asStrings().map { "$packageId * $it" }
    }

    sealed class ClassLevel(fqClassName: String, actions: Set<AccessTypes>, validActions: Set<AccessTypes>) : PolicyAllowance(fqClassName, actions, validActions) {
        val packageId = fqnTarget.substringBeforeLast('.')
        val classId = fqnTarget.substringAfterLast('.')

        override fun asPolicyStrings(): List<String> {
            val sig = sigPart()

            val classLevel = actions.addDefaultClassActions().filter { it in ALL_CLASS_ACCESS_TYPES }
                    .asStrings()
                    .map { access -> "$packageId $classId $access" }
            val classMember = actions.filterNot { it in ALL_CLASS_ACCESS_TYPES }
                    .asStrings()
                    .map { access -> "$packageId $classId.$sig $access" }
            return (classLevel + classMember).toSet().toList()
        }

        private fun makeList(mandatory: String, explodedExtras: List<String>, explode: Boolean): List<String> {
            return listOf(mandatory) + if (explode) explodedExtras else emptyList()
        }

        override fun asCheckStrings(explode: Boolean): List<String> {
            val classLevel = actions.filter { it in ALL_CLASS_ACCESS_TYPES }.asStrings()
                    .map {
                        makeList("$packageId $classId $it",
                                listOf("$packageId * $it"), explode)
                    }.flatten()
            val classMember = actions.filterNot { it in ALL_CLASS_ACCESS_TYPES }.asStrings()
                    .map {
                        makeList("$packageId $classId.${sigPart()} $it",
                                listOf("$packageId $classId.* $it", "$packageId * $it"), explode)
                    }.flatten()
            return (classLevel + classMember).toSet().toList()
        }

        protected abstract fun sigPart(): String

        class ClassAccess(fqClassName: String, actions: Set<AccessTypes>) : ClassLevel(fqClassName, actions, ALL_CLASS_ACCESS_TYPES) {
            override fun sigPart(): String = ""
        }

        class ClassMethodAccess(fqClassName: String, val methodName: String, _methodSig: String, actions: Set<AccessTypes>) : ClassLevel(fqClassName, actions, ALL_CLASS_METHOD_ACCESS_TYPES) {
            val methodSig: String = _methodSig.replace('/', '.')
            override fun sigPart(): String = if (methodName == "*") "*" else "$methodName$methodSig"
        }

        class ClassConstructorAccess(fqClassName: String, _constructorSig: String, actions: Set<AccessTypes>) : ClassLevel(fqClassName, actions, ALL_CLASS_CONSTUCTOR_ACCESS_TYPES) {
            val constructorSig: String = _constructorSig.replace('/', '.')
            override fun sigPart(): String = if (constructorSig == "*") "*" else "<init>:${constructorSig.substringBefore(')') + ")L${fqnTarget};"}"
        }

        class ClassFieldAccess(fqClassName: String, val fieldName: String, _fieldTypeSig: String, actions: Set<AccessTypes>) : ClassLevel(fqClassName, actions, ALL_CLASS_FIELD_ACCESS_TYPES) {
            val fieldTypeSig: String = _fieldTypeSig.replace('/', '.')
            override fun sigPart(): String = "$fieldName:$fieldTypeSig"
        }

        class ClassPropertyAccess(fqClassName: String, val propertyName: String, _propertyTypeSig: String, actions: Set<AccessTypes>) : ClassLevel(fqClassName, actions, ALL_CLASS_PROP_ACCESS_TYPES) {
            val propertyTypeSig: String = _propertyTypeSig.replace('/', '.')
            override fun sigPart(): String = if (propertyName == "*") "@*" else "@$propertyName:$propertyTypeSig"
        }
    }
}

fun AccessPolicies.toPolicy(): List<String> = this.map { it.asPolicyStrings() }.flatten().toSet().sorted()
