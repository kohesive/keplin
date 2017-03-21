package uy.kohesive.chillambda

import org.junit.Test
import uy.kohesive.chillamda.ClassSerDesUtil
import java.io.Serializable

class TestSerializationLambdas : Serializable {
    @Test
    fun testActualThing() {
        internal()
    }

    fun internal() {
        val z = 1
        val s = "jayson"

        val f = fun MyReceiver.(): Any? = s + z + 10
        val r1 = ClassSerDesUtil.serializeLambdaToBase64<MyReceiver, Any> { val x = 10; s + x + z + _score + doc["some"] + ctx["fish"] + this._value + docInt("testField", 1) + parmInt("testParm", 1) }
        val x1 = ClassSerDesUtil.deserFromPrefixedBase64<MyReceiver, Any>(r1)

        val r2 = ClassSerDesUtil.serializeLambdaToBase64<MyReceiver, Any>(f)
        val x2 = ClassSerDesUtil.deserFromPrefixedBase64<MyReceiver, Any>(r2)

        val r3 = ClassSerDesUtil.serializeLambdaToBase64<MyReceiver, Any>(Unrelated.f2)
        val x3 = ClassSerDesUtil.deserFromPrefixedBase64<MyReceiver, Any>(r3)
    }

}

class MyReceiver(val _score: Double, val doc: Map<String, String>, val parm: Map<String, String>, val ctx: Map<String, String>, val _value: Any) {
    fun docInt(field: String, default: Int): Int = doc.get(field)?.toInt() ?: default
    fun parmInt(field: String, default: Int): Int = parm.get(field)?.toInt() ?: default
}

object Unrelated {
    val membery = 10
    val f2 = fun MyReceiver.(): Any? = uy.kohesive.chillambda.Unrelated.membery
}
