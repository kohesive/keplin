package uy.kohesive.keplin.elasticsaerch.kotlinscript

import org.junit.Test
import uy.kohesive.keplin.elasticsearch.kotlinscript.ClassSerDesUtil
import uy.kohesive.keplin.elasticsearch.kotlinscript.EsKotlinScriptTemplate
import java.io.*

class Goo(val special: String = "special")

class Foo : Serializable {

    fun bar() {

        val x = 5
        val name = "jayson"
        val xyz = "xyz"

        val f = fun Goo.(z: Int, s: String): String = s + name + x + xyz + special

        Goo("whatever").f(55, "zzz")
        doIt(10, "asdf", f)
        doIt(33, "pop") { i, s -> special + s + i + x }
    }

    fun doIt(x: Int, name: String, f: Goo.(Int, String) -> String): String {
        testSerialize(f)
        return Goo("hater").f(x, name)
    }

    @Test
    fun testLambdaThingy() {
        Foo().bar()
    }

    @Test
    fun testActualThing() {
        val z = 1
        val s = "jayson"

        val f = fun EsKotlinScriptTemplate.(): Any? = s + z + 10
        val r1 = ClassSerDesUtil.serializeLambdaToBase64 { val x = 10; s + x + z }
        val x1 = ClassSerDesUtil.deserFromPrefixedBase64(r1)

        val r2 = ClassSerDesUtil.serializeLambdaToBase64(f)
        val x2 = ClassSerDesUtil.deserFromPrefixedBase64(r2)

        val r3 = ClassSerDesUtil.serializeLambdaToBase64(f2)
        val x3 = ClassSerDesUtil.deserFromPrefixedBase64(r3)
    }

    val membery = 10
    val f2 = fun EsKotlinScriptTemplate.(): Any? = membery

    @Suppress("UNCHECKED_CAST")
    fun testSerialize(thing: Any) {
        val bytes = ByteArrayOutputStream().apply {
            ObjectOutputStream(this).use { stream ->
                stream.writeObject(thing)
            }
        }.toByteArray()

        val otherThing = ObjectInputStream(ByteArrayInputStream(bytes)).use { stream ->
            stream.readObject()
        } as Function3<Goo, Int, String, String>

        val firstThing = thing as Function3<Goo, Int, String, String>

        val goo = Goo("revived")
        println(firstThing.invoke(goo, 22, "deser"))
        println(otherThing.invoke(goo, 22, "deser"))
        println()
    }
}

