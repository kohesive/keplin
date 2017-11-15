package uy.kohesive.chillambda

import org.junit.Test
import uy.kohesive.chillamda.Chillambda
import uy.kohesive.cuarentena.Cuarentena
import uy.kohesive.cuarentena.policy.AccessTypes
import uy.kohesive.cuarentena.policy.PolicyAllowance
import uy.kohesive.cuarentena.policy.toPolicy
import java.io.Serializable

class TestSerializationLambdas : Serializable {
    @Test
    fun testActualThing() {
        internal()
    }

    val receiverCuarentenaPolicies = listOf(
            PolicyAllowance.ClassLevel.ClassAccess(MyReceiver::class.java.canonicalName, setOf(AccessTypes.ref_Class_Instance)),
            PolicyAllowance.ClassLevel.ClassMethodAccess(MyReceiver::class.java.canonicalName, "*", "*", setOf(AccessTypes.call_Class_Instance_Method)),
            PolicyAllowance.ClassLevel.ClassPropertyAccess(MyReceiver::class.java.canonicalName, "*", "*", setOf(AccessTypes.read_Class_Instance_Property))
    ).toPolicy().toSet()

    val chillambda = Chillambda(Cuarentena(Cuarentena.painlessPlusKotlinPolicy + receiverCuarentenaPolicies))

    fun internal() {
        val z = 1
        val s = "jayson"

        val f = fun MyReceiver.(): Any? = s + z + 10
        val r1 = chillambda.serializeLambdaToBase64<MyReceiver, Any> { val x = 10; s + x + z + _score + doc["some"] + ctx["fish"] + this._value + docInt("testField", 1) + parmInt("testParm", 1) }
        val x1 = chillambda.deserFromPrefixedBase64<MyReceiver, Any>(r1)

        val r2 = chillambda.serializeLambdaToBase64<MyReceiver, Any>(f)
        val x2 = chillambda.deserFromPrefixedBase64<MyReceiver, Any>(r2)

        val r3 = chillambda.serializeLambdaToBase64<MyReceiver, Any>(Unrelated.f2)
        val x3 = chillambda.deserFromPrefixedBase64<MyReceiver, Any>(r3)
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
