package uy.kohesive.cuarentena.policy


// TODO: access types that are manipulations:  i.e. remove all calls to println and java.io.PrintStream and System.out
//  i.e. null out references to objects during serializations that are not accessed
// TODO: other access types
//  allow null references to some objects, but not actual references

enum class AccessTypes {
    ref_Class,
    ref_Class_Static,
    ref_Class_Instance,

    read_Class_Static_Field,
    write_Class_Static_Field,

    read_Class_Instance_Field,
    write_Class_Instance_Field,

    call_Class_Constructor,
    call_Class_Static_Method,
    call_Class_Instance_Method,

    read_Class_Static_Property,
    write_Class_Static_Property,

    read_Class_Instance_Property,
    write_Class_Instance_Property,
}

// TODO: signed JAR's, can we detect this for a package if it came from one, that is verified and matches a signer we expect?

val ALL_PACKAGE_ACCESS_TYPES = enumValues<AccessTypes>().toSet()

val ALL_CLASS_ACCESS_TYPES = setOf(AccessTypes.ref_Class, AccessTypes.ref_Class_Static, AccessTypes.ref_Class_Instance)

val ALL_CLASS_CONSTUCTOR_ACCESS_TYPES = setOf(AccessTypes.call_Class_Constructor)

val ALL_CLASS_METHOD_ACCESS_TYPES = setOf(AccessTypes.call_Class_Static_Method, AccessTypes.call_Class_Instance_Method)

val ALL_CLASS_FIELD_ACCESS_TYPES = setOf(AccessTypes.read_Class_Static_Field, AccessTypes.write_Class_Static_Field,
        AccessTypes.read_Class_Instance_Field, AccessTypes.write_Class_Instance_Field)

val ALL_CLASS_PROP_ACCESS_TYPES = setOf(AccessTypes.read_Class_Static_Property, AccessTypes.write_Class_Static_Property,
        AccessTypes.read_Class_Instance_Property, AccessTypes.write_Class_Instance_Property)

internal val STATIC_CLASS_ACCESS_TYPES = setOf(AccessTypes.read_Class_Static_Field, AccessTypes.write_Class_Static_Field,
        AccessTypes.read_Class_Static_Property, AccessTypes.write_Class_Static_Property,
        AccessTypes.call_Class_Static_Method)

internal val INSTANCE_CLASS_ACCESS_TYPES = setOf(AccessTypes.call_Class_Constructor, AccessTypes.call_Class_Instance_Method,
        AccessTypes.read_Class_Instance_Field, AccessTypes.write_Class_Instance_Field,
        AccessTypes.read_Class_Instance_Property, AccessTypes.write_Class_Instance_Property)

internal fun Set<AccessTypes>.asStrings() = this.map { it.name }.sorted()
internal fun List<AccessTypes>.asStrings() = this.map { it.name }.sorted()

internal fun Set<AccessTypes>.addDefaultClassActions(): Set<AccessTypes> {
    val temp = this + if (this.any { it in STATIC_CLASS_ACCESS_TYPES }) listOf(AccessTypes.ref_Class_Static) else emptyList<AccessTypes>() +
            if (this.any { it in INSTANCE_CLASS_ACCESS_TYPES }) listOf(AccessTypes.ref_Class_Instance) else emptyList<AccessTypes>()

    return if (AccessTypes.ref_Class !in temp && ALL_CLASS_ACCESS_TYPES.any { it in temp }) temp + AccessTypes.ref_Class else temp
}
