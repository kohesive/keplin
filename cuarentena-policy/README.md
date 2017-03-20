### Current policy types:

####  Class Specific

*  Class T - Class<T> can be loaded, static referenced, and/or instance referenced
*  Class method access (instance and static)
*  Class property read/write access (instance and static, simplified case of using javabean get/is/has/set/with methods including fluent versions)
*  Class field access (instance and static)

> ... for Kotlin Companion objects the definition must be on the Companion class for access to its methods, they are not treated as static
> ... for Kotlin Extension methods they must be referenced for their actual compiled class and static method name

#### Package Level

_Class level is preferred, but some host applications control the whole package.  The packages should be sealed._

For a given package:

* Enforce that package must be sealed 
* All package classes can be loaded, static referenced, and/or instance referenced
* All package class instance methods can be called
* All package class static methods can be called
* All package class instance properties can be accessed with combination of read / write
* All package class static properties can be accessed with combination of read / write
* All package class instance field accessed with combination of read / write
* All package class static field accessed with combination of read / write

#### Class Serialization

Scope of serialization rules
             
* Allow containing classes
* Allow contained classes (by Parent$child naming pattern only)
* Allow other classes (caused by references, or inner/contained that do not follow a Parent$child naming pattern)

Class specific policies
             
* Classes that can be referenced  (the Class<T> would be loaded because of the reference)
* Other Class instances that can be ser/deser (the other instance of T can be serialized, and the Class<T> would be loaded on deser)
* Other Class instance references allowed if null (the reference to T must be null, and the Class<T> might be loaded on deser)
* Other CLass instance references that will be nulled (the reference to T is forced to null, and the Class<T> might be loaded on deser)
