package uy.kohesive.cuarentena

import org.junit.Test
import uy.kohesive.cuarentena.policy.ALL_CLASS_ACCESS_TYPES
import uy.kohesive.cuarentena.policy.PolicyAllowance
import uy.kohesive.cuarentena.policy.toPolicy
import java.io.*

// TODO: This is not really a test, it is a manual display of what happens, write a real test

class TestClassAllowanceDetector {
    val globalPolicies = Cuarentena.painlessPlusKotlinPolicy

    @Test
    fun testSeeAllImportantThings() {
        val y = 20
        val result = ClassAllowanceDetector.scanClassByteCodeForDesiredAllowances(lambdaToBytes {
            val x = 10 + y
            println(x)
            File("asd")

            emptyList<String>()
            listOf("a")
            listOf("a", "b")
            listOf(1, 2, 3)

            emptySet<String>()
            setOf("a")
            setOf("a", "b")
            setOf(1, 2, 3)

            val lists = listOf("a") + listOf("b") + listOf("c", "d", "e") + listOf("z")

            val s = "stringy $x is next to these $lists"
            val s2 = """$s what $s"""

            val r = """[\w\d]+""".toRegex()
            val p = """[\w\d]+""".toPattern()

            Unit
        })

        result.print()
    }

    private fun ClassAllowanceDetector.ScanState.print() {
        println()
        println("=====[ Classes scan results: ]=======================")
        println()
        println("Creates Classes:")
        println()
        this.createsClass.forEach(::println)
        println()
        println("Creates Methods:")
        println()
        this.createsMethods.forEach(::println)
        println()
        println("Creates Fields:")
        println()
        this.createsFields.forEach(::println)
        println()
        println("Allowances requested:  (violations marked with '!!'")
        println()
        this.allowances.toPolicy().forEach {
            val isViolation = if (it !in globalPolicies) "!!" else "  "
            println("$isViolation  $it")
        }

    }

    private fun lambdaToBytes(lambda: () -> Any?): List<NamedClassBytes> {
        val serClass = lambda.javaClass
        val className = serClass.name

        val tracedClasses = mutableSetOf<Class<out Any>>()

        val serializedBytes = ByteArrayOutputStream().apply {
            TraceUsedClassesObjectOutputStream(this, tracedClasses).use { stream ->
                stream.writeObject(lambda)
            }
        }.toByteArray()

        val outerClasses = generateSequence<Class<*>>(serClass) { seed -> seed.declaringClass.takeIf { it != seed } } +
                generateSequence<Class<*>>(serClass) { seed -> seed.enclosingClass.takeIf { it != seed } }
        val innerClasses = generateSequence<List<Class<*>>>(listOf<Class<*>>(serClass)) { seed ->
            val l = seed.map { it.classes.filterNot { it == seed }.toList() }.flatten()
            if (l.isEmpty()) null else l
        }.flatten()

        val allClasses = (innerClasses + outerClasses).toSet()

        val classesAsBytes = tracedClasses.filterNot {
            // filter out anything we are allowed to access, because we consider that "outside" of our class to serialize
            PolicyAllowance.ClassLevel.ClassAccess(it.name, ALL_CLASS_ACCESS_TYPES).asCheckStrings(true).any { it in globalPolicies }
        }.filter {
            // only classes related to the lambda and its containing class matter
            it in allClasses // it.name == outerClass || it.name.startsWith(outerClass+'$')
        }.map { oneClass ->
            loadClassAsBytes(oneClass.name, oneClass.classLoader)
        }

        return classesAsBytes
    }

    private fun loadClassAsBytes(className: String, loader: ClassLoader = Thread.currentThread().contextClassLoader): NamedClassBytes {
        return NamedClassBytes(className,
                loader.getResourceAsStream(className.replace('.', '/') + ".class").use { it.readBytes() })
    }

    class TraceUsedClassesObjectOutputStream(output: OutputStream, val classes: MutableSet<Class<out Any>> = mutableSetOf()) : ObjectOutputStream(output) {
        // TODO: don't need this method?
        override fun annotateClass(cl: Class<*>) {
            classes.add(cl)
            super.annotateClass(cl)
        }

        // TODO: don't need this method?
        override fun annotateProxyClass(cl: Class<*>) {
            classes.add(cl)
            super.annotateProxyClass(cl)
        }

        override fun writeClassDescriptor(desc: ObjectStreamClass) {
            desc.forClass()?.let { classes.add(it) }
            super.writeClassDescriptor(desc)
        }
    }
}
