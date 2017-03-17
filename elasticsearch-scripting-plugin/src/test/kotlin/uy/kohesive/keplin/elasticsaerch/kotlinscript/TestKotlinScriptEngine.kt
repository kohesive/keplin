package uy.kohesive.keplin.elasticsaerch.kotlinscript

import org.elasticsearch.action.search.SearchRequestBuilder
import org.elasticsearch.client.Client
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.xcontent.XContentFactory
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.plugins.Plugin
import org.elasticsearch.script.Script
import org.elasticsearch.script.ScriptType
import org.elasticsearch.test.ESIntegTestCase
import org.junit.Before
import uy.kohesive.keplin.elasticsearch.kotlinscript.ClassSerDesUtil
import uy.kohesive.keplin.elasticsearch.kotlinscript.EsKotlinScriptTemplate
import uy.kohesive.keplin.elasticsearch.kotlinscript.KotlinScriptPlugin
import kotlin.system.measureTimeMillis

@ESIntegTestCase.ClusterScope(transportClientRatio = 1.0, numDataNodes = 1)
class TestKotlinScriptEngine : ESIntegTestCase() {
    override fun nodeSettings(nodeOrdinal: Int): Settings {
        val temp = super.nodeSettings(nodeOrdinal)
        var builder = Settings.builder().apply {
            temp.asMap.forEach {
                put(it.key, it.value)
            }
            put(KotlinScriptPlugin.KotlinPath, "/Users/jminard/Downloads/kotlinc")
        }
        return builder.build()
    }

    companion object {
        val INDEX_NAME = "test"
    }

    private lateinit var client: Client

    override fun nodePlugins(): Collection<Class<out Plugin>> = listOf(KotlinScriptPlugin::class.java)

    fun <T : Any?> SearchRequestBuilder.addScriptField(name: String, params: Map<String, Any> = emptyMap(), lambda: EsKotlinScriptTemplate.() -> T): SearchRequestBuilder {
        return this.addScriptField(name, Script(ScriptType.INLINE, "kotlin", ClassSerDesUtil.serializeLambdaToBase64(lambda), params))
    }

    fun SearchRequestBuilder.addScriptField(name: String, params: Map<String, Any> = emptyMap(), scriptCode: String): SearchRequestBuilder {
        return this.addScriptField(name, Script(ScriptType.INLINE, "kotlin", scriptCode, params))
    }

    fun testNormalQuery() {
        println("NORMAL QUERY:")
        val prep = client.prepareSearch(INDEX_NAME)
                .setQuery(QueryBuilders.matchAllQuery())
        val results1 = prep.execute().actionGet()
        if (results1.hits == null || results1.hits.hits == null) {
            fail("no data")
        }
        results1.hits.hits.forEachIndexed { idx, hit ->
            println("[$idx] ${hit.id} => ${hit.fields["multi"]!!.first()}")
        }
        println("----end----")
    }

    fun testLambdaAsScript() {
        println("Elasticsearch Kotlin Script starting")
        val prep = client.prepareSearch(INDEX_NAME)
                .addScriptField("multi", mapOf("multiplier" to 2)) {
                    docInt("number", 1) * parmInt("multiplier", 1) + _score
                }.setQuery(QueryBuilders.matchAllQuery())

        (1..25).forEach { idx ->
            println("...RUN $idx :>>>>")
            val time = measureTimeMillis {
                val results1 = prep.execute().actionGet()
                results1.hits.hits.forEachIndexed { idx, hit ->
                    println("[$idx] ${hit.id} => ${hit.fields["multi"]!!.first()}")
                }
            }
            println("  ${time}ms")
        }
        println("...END Kotlin Script run")
    }

    fun testStringScript() {
        println("Elasticsearch Kotlin Script starting")
        val prep = client.prepareSearch(INDEX_NAME)
                .addScriptField("multi", mapOf("multiplier" to 2), """
                    docInt("number", 1) * parmInt("multiplier", 1) + _score
                """).setQuery(QueryBuilders.termQuery("title", "Title"))

        (1..25).forEach { idx ->
            println("...RUN $idx :>>>>")
            val time = measureTimeMillis {
                val results1 = prep.execute().actionGet()
                results1.hits.hits.forEachIndexed { idx, hit ->
                    println("[$idx] ${hit.id} => ${hit.fields["multi"]!!.first()}")
                }
            }
            println("  ${time}ms")
        }
        println("...END Kotlin Script run")
    }

    @Before
    fun createTestIndex() {
        // Delete any previously indexed content.
        client = ESIntegTestCase.client()

        if (client.admin().indices().prepareExists(INDEX_NAME).get().isExists) {
            client.admin().indices().prepareDelete(INDEX_NAME).get()
        }

        val bulk = client.prepareBulk()

        (1..5).forEach { i ->
            bulk.add(client.prepareIndex()
                    .setIndex(INDEX_NAME)
                    .setType("test")
                    .setId(i.toString())
                    .setSource(XContentFactory.jsonBuilder()
                            .startObject()
                            .field("url", "http://google.com/$i")
                            .field("title", "Title #$i")
                            .field("content", "Hello World $i!")
                            .field("number", i)
                            .endObject()
                    )
            )
        }

        bulk.execute().actionGet()

        flushAndRefresh(INDEX_NAME)
        ensureGreen(INDEX_NAME)
    }
}