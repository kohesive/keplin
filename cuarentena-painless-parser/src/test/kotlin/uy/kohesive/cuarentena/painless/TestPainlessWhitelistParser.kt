package uy.kohesive.cuarentena.painless

import org.junit.Test
import uy.kohesive.cuarentena.policy.toPolicy

class TestPainlessWhitelistParser {
    @Test
    fun testParseAllPainlessWhitelistsWithoutExceptions() {
        PainlessWhitelistParser().readDefinitions().let {
            println()
            println()
            println("====[ Results: ]=======================================")
            println()
            println()
            it.toPolicy().map {
                val parts = it.split(' ')
                if (parts.size != 3) throw IllegalStateException("Odd format!")
                parts[0].padEnd(30) + " " + parts[1].padEnd(100) + " " + parts[2]
            }.forEach(::println)
        }
    }
}