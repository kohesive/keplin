package uy.kohesive.keplin.elasticsaerch.kotlinscript

import org.junit.Test
import uy.kohesive.keplin.elasticsearch.kotlinscript.ClassSerDesUtil
import uy.kohesive.keplin.elasticsearch.kotlinscript.EsKotlinScriptTemplate
import java.io.Serializable

class TestSerializationLambdas : Serializable {
    @Test
    fun testActualThing() {
        internal()
    }

    fun internal() {
        val z = 1
        val s = "jayson"

        val f = fun EsKotlinScriptTemplate.(): Any? = s + z + 10
        val r1 = ClassSerDesUtil.serializeLambdaToBase64 { val x = 10; s + x + z + _score + doc["some"] + ctx["fish"] + this._value + docInt("testField", 1) + parmInt("testParm", 1) }
        val x1 = ClassSerDesUtil.deserFromPrefixedBase64(r1)

        val r2 = ClassSerDesUtil.serializeLambdaToBase64(f)
        val x2 = ClassSerDesUtil.deserFromPrefixedBase64(r2)

        val r3 = ClassSerDesUtil.serializeLambdaToBase64(Unrelated.f2)
        val x3 = ClassSerDesUtil.deserFromPrefixedBase64(r3)
    }

}

object Unrelated {
    val membery = 10
    val f2 = fun EsKotlinScriptTemplate.(): Any? = membery
}
