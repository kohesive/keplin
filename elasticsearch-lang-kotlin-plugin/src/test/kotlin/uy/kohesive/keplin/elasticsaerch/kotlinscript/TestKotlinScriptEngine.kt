@file:Suppress("NOTHING_TO_INLINE")

package uy.kohesive.keplin.elasticsaerch.kotlinscript

import org.elasticsearch.action.search.SearchRequestBuilder
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.client.Client
import org.elasticsearch.common.bytes.BytesReference
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.xcontent.XContentFactory
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.ingest.common.IngestCommonPlugin
import org.elasticsearch.painless.PainlessPlugin
import org.elasticsearch.plugins.Plugin
import org.elasticsearch.script.Script
import org.elasticsearch.script.ScriptType
import org.elasticsearch.search.SearchHit
import org.elasticsearch.test.ESIntegTestCase
import org.junit.Before
import uy.kohesive.keplin.elasticsearch.kotlinscript.ConcreteEsKotlinScriptTemplate
import uy.kohesive.keplin.elasticsearch.kotlinscript.EsKotlinScriptTemplate
import uy.kohesive.keplin.elasticsearch.kotlinscript.KotlinScriptEngineService
import uy.kohesive.keplin.elasticsearch.kotlinscript.KotlinScriptPlugin
import java.io.File
import java.util.regex.Pattern
import kotlin.system.measureTimeMillis

@ESIntegTestCase.ClusterScope(transportClientRatio = 1.0, numDataNodes = 1)
class TestKotlinScriptEngine : ESIntegTestCase() {
    override fun nodeSettings(nodeOrdinal: Int): Settings {
        val temp = super.nodeSettings(nodeOrdinal)
        var builder = Settings.builder().apply {
            temp.asMap.forEach {
                put(it.key, it.value)
            }
            put("script.painless.regex.enabled", true)
        }
        return builder.build()
    }

    companion object {
        val INDEX_NAME = "test"
    }

    private lateinit var client: Client

    override fun nodePlugins(): Collection<Class<out Plugin>> =
            listOf(KotlinScriptPlugin::class.java,
                    IngestCommonPlugin::class.java,
                    PainlessPlugin::class.java)

    fun testNormalQuery() {

        val prep = client.prepareSearch(INDEX_NAME)
                .setQuery(QueryBuilders.matchQuery("title", "title"))
                .setFetchSource(true)
        val results = prep.execute().actionGet()

        results.assertHasResults()
        results.printHitsSourceField("title")

    }

    fun testMoreComplexPainlessScript() {
        val prep = client.prepareSearch(INDEX_NAME)
                .addScriptField("scriptField1", Script(ScriptType.INLINE, "painless", """
                    String currentValue = doc['badContent'][0]; // bad, need to handle multiple values but is painful

                    Pattern badCategoryRegex = /^(\w+)\s*\:\s*(.+)$/;
                    if (currentValue ==~ badCategoryRegex) {
                        String[] parts = /:/.split(currentValue);
                        String typeName = parts[0];
                        /,/.splitAsStream(parts[1]).map(item -> typeName + ": " + item.trim()).collect(Collectors.toList())
                    } else {
                        currentValue;
                    }
                  """, emptyMap()))
                .setQuery(QueryBuilders.matchQuery("title", "title"))
                .setFetchSource(true)

        prep.runManyTimes {
            printHitsField("scriptField1")
        }
    }

    fun testStringScript() {
        val prep = client.prepareSearch(INDEX_NAME)
                .addScriptField("scriptField1", mapOf("multiplier" to 2), """
                    docInt("number", 1) * parmInt("multiplier", 1) + _score
                """).setQuery(QueryBuilders.matchQuery("title", "title"))
                .setFetchSource(true)

        prep.runManyTimes {
            printHitsField("scriptField1")
        }
    }

    fun testSecurityViolationInStringScript() {
        try {
            val response = client.prepareSearch(INDEX_NAME)
                    .addScriptField("scriptField1", mapOf("multiplier" to 2), """
                    import java.io.*

                    val f = File("howdy")  // violation!

                    docInt("number", 1) * parmInt("multiplier", 1) + _score
                """).setQuery(QueryBuilders.matchQuery("title", "title"))
                    .setFetchSource(true).execute().actionGet()
            fail("security verification should have caught this use of File")
        } catch (ex: Exception) {
            val exceptionStack = generateSequence(ex as Throwable) { it.cause }
            assertTrue(exceptionStack.take(5).any { "java.io.File" in it.message!! })
        }
    }

    fun testMoreComplexKotlinAsScript() {
        val prep = client.prepareSearch(INDEX_NAME)
                .addScriptField("scriptField1", Script(ScriptType.INLINE, "kotlin", """
                    val currentValue = docString("badContent") ?: ""
                    "^(\\w+)\\s*\\:\\s*(.+)$".toRegex().matchEntire(currentValue)
                            ?.takeIf { it.groups.size > 2 }
                            ?.let {
                                val typeName = it.groups[1]!!.value.toLowerCase()
                                it.groups[2]!!.value.split(',')
                                        .map { it.trim().toLowerCase() }
                                        .filterNot { it.isBlank() }
                                        .map { typeName + ": " + it }
                            } ?: listOf(currentValue)
                  """, emptyMap()))
                .setQuery(QueryBuilders.matchQuery("title", "title"))
                .setFetchSource(true)

        prep.runManyTimes {
            printHitsField("scriptField1")
        }
    }

    fun testLambdaAsScript() {
        val prep = client.prepareSearch(INDEX_NAME)
                .addScriptField("scriptField1", mapOf("multiplier" to 2)) {
                    docInt("number", 1) * parmInt("multiplier", 1) + _score
                }.setQuery(QueryBuilders.matchQuery("title", "title"))
                .setFetchSource(true)

        prep.runManyTimes {
            printHitsField("scriptField1")
        }
    }

    fun testSecurityViolationInLambdaAsScript() {
        try {
            val response = client.prepareSearch(INDEX_NAME)
                    .addScriptField("multi", mapOf("multiplier" to 2)) {
                        val f = File("asdf") // security violation
                        docInt("number", 1) * parmInt("multiplier", 1) + _score
                    }.setQuery(QueryBuilders.matchQuery("title", "title"))
                    .setFetchSource(true).execute().actionGet()
            fail("security verification should have caught this use of File")
        } catch (ex: Exception) {
            val exceptionStack = generateSequence(ex as Throwable) { it.cause }
            assertTrue(exceptionStack.take(5).any { "java.io.File" in it.message!! })
        }
    }

    fun testMoreComplexLambdaAsScript() {
        val badCategoryPattern = """^(\w+)\s*\:\s*(.+)$""".toPattern() // Pattern is serializable, Regex is not
        val prep = client.prepareSearch(INDEX_NAME)
                .addScriptField("scriptField1", emptyMap()) {
                    val currentValue = docStringList("badContent")
                    currentValue.map { value -> badCategoryPattern.toRegex().matchEntire(value)?.takeIf { it.groups.size > 2 } }
                            .filterNotNull()
                            .map {
                                val typeName = it.groups[1]!!.value.toLowerCase()
                                it.groups[2]!!.value.split(',')
                                        .map { it.trim().toLowerCase() }
                                        .filterNot { it.isBlank() }
                                        .map { "$typeName: $it" }
                            }.flatten()
                }.setQuery(QueryBuilders.matchQuery("title", "title"))
                .setFetchSource(true)

        prep.runManyTimes {
            printHitsField("scriptField1")
        }
    }

    fun testFuncRefWithMockContextAndRealDeal() {
        val badCategoryPattern = """^(\w+)\s*\:\s*(.+)$""".toPattern() // Pattern is serializable, Regex is not
        val scriptFunc = fun EsKotlinScriptTemplate.(): Any? {
            val currentValue = docStringList("badContent")
            return currentValue.map { value -> badCategoryPattern.toRegex().matchEntire(value)?.takeIf { it.groups.size > 2 } }
                    .filterNotNull()
                    .map {
                        val typeName = it.groups[1]!!.value.toLowerCase()
                        it.groups[2]!!.value.split(',')
                                .map { it.trim().toLowerCase() }
                                .filterNot { it.isBlank() }
                                .map { "$typeName: $it" }
                    }.flatten()
        }

        val mockContext = ConcreteEsKotlinScriptTemplate(parm = emptyMap(),
                doc = mutableMapOf("badContent" to mutableListOf<Any>("category:  History, Science, Fish")),
                ctx = mutableMapOf(), _value = 0, _score = 0.0)

        val expectedResults = listOf("category: history", "category: science", "category: fish")

        assertEquals(expectedResults, mockContext.scriptFunc())

        val prep = client.prepareSearch(INDEX_NAME)
                .addScriptField("scriptField1", emptyMap(), scriptFunc)
                .setQuery(QueryBuilders.matchQuery("title", "title"))
                .setFetchSource(true)

        prep.runManyTimes {
            printHitsField("scriptField1")
        }
    }

    fun testLambdaAsIngestPipelineStep() {
        val badCategoryPattern = """^(\w+)\s*\:\s*(.+)$""".toPattern() // Pattern is serializable, Regex is not
        val scriptFunc = fun EsKotlinScriptTemplate.(): Any? {
            val newValue = (ctx["badContent"] as? String)
                    ?.let { currentValue ->
                        badCategoryPattern.takeIfMatching(currentValue, 3)
                                ?.let {
                                    val typeName = it.groups[1]!!.value.toLowerCase()
                                    it.groups[2]!!.value.split(',')
                                            .map { it.trim().toLowerCase() }
                                            .filterNot { it.isBlank() }
                                            .map { "$typeName: $it" }
                                } ?: listOf(currentValue)
                    } ?: emptyList()
            ctx["badContent"] = newValue
            return true
        }

        val simulateSource = makeSimulatePipelineJsonForLambda(scriptFunc)

        val simulateResults = client.admin().cluster().prepareSimulatePipeline(simulateSource).execute().actionGet()
        simulateResults.results[0]!!.let {
            val expectedResults = listOf("category: history", "category: science", "category: fish")
            // TODO: no response parsing is in the client, need to handle this specially
        }

    }

    @Before
    fun createTestIndex() {
        // Delete any previously indexed content.
        client = ESIntegTestCase.client()

        if (client.admin().indices().prepareExists(INDEX_NAME).get().isExists) {
            client.admin().indices().prepareDelete(INDEX_NAME).get()
        }

        val bulk = client.prepareBulk()

        client.admin().indices().prepareCreate(INDEX_NAME).setSource("""
               {
                  "settings": {
                    "index": {
                      "number_of_shards": "2",
                      "number_of_replicas": "0"
                    }
                  },
                  "mappings": {
                    "test": {
                      "_all": {
                        "enabled": false
                      },
                      "properties": {
                        "url": { "type": "keyword" },
                        "title": { "type": "text" },
                        "content": { "type": "text" },
                        "number": { "type": "integer" },
                        "badContent": { "type": "keyword" }
                      }
                    }
                  }
               }
        """).execute().actionGet()

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
                            .field("badContent", "category:  History, Science, Fish")
                            // badContent is incorrect, should be multi-value
                            // ["category: history", "category: science", "category: fish"]
                            .endObject()
                    )
            )
        }

        bulk.execute().actionGet()

        flushAndRefresh(INDEX_NAME)
        ensureGreen(INDEX_NAME)
    }


    fun <T : Any?> SearchRequestBuilder.addScriptField(name: String, params: Map<String, Any> = emptyMap(), lambda: EsKotlinScriptTemplate.() -> T): SearchRequestBuilder {
        return this.addScriptField(name, Script(ScriptType.INLINE, "kotlin", KotlinScriptEngineService.chillambda.serializeLambdaToBase64<EsKotlinScriptTemplate, Any>(lambda), params))
    }

    fun <T : Any?> makeSimulatePipelineJsonForLambda(lambda: EsKotlinScriptTemplate.() -> T): BytesReference {
        val pipelineDef = XContentFactory.jsonBuilder()
                .startObject()
                .field("description", "Kotlin lambda test pipeline")
                .startArray("processors")
                .startObject()
                .startObject("script")
                .field("lang", "kotlin")
                .field("inline", KotlinScriptEngineService.chillambda.serializeLambdaToBase64<EsKotlinScriptTemplate, Any>(lambda))
                .startObject("params").endObject()
                .endObject()
                .endObject()
                .endArray()
                .endObject()

        val simulationJson = XContentFactory.jsonBuilder()
                .startObject()
                .rawField("pipeline", pipelineDef.bytes())
                .startArray("docs")
                .startObject().startObject("_source").field("id", 1).field("badContent", "category:  History, Science, Fish").endObject().endObject()
                .startObject().startObject("_source").field("id", 2).field("badContent", "category:  monkeys, mountains").endObject().endObject()
                .endArray()
                .endObject()

        return simulationJson.bytes()
    }

    fun SearchRequestBuilder.addScriptField(name: String, params: Map<String, Any> = emptyMap(), scriptCode: String): SearchRequestBuilder {
        return this.addScriptField(name, Script(ScriptType.INLINE, "kotlin", scriptCode, params))
    }

    fun SearchHit.printHitSourceField(fieldName: String) {
        println("${id} => ${sourceAsMap()["title"].toString()}")
    }

    fun SearchHit.printHitField(fieldName: String) {
        println("${id} => ${fields[fieldName]?.values.toString()}")
    }

    fun SearchResponse.printHitsSourceField(fieldName: String) {
        hits.hits.forEach { it.printHitSourceField(fieldName) }
    }

    fun SearchResponse.printHitsField(fieldName: String) {
        hits.hits.forEach { it.printHitField(fieldName) }
    }

    fun SearchResponse.assertHasResults() {
        if (hits == null || hits.hits == null || hits.hits.isEmpty()) fail("no data returned in query")
    }

    fun SearchRequestBuilder.runManyTimes(func: SearchResponse.() -> Unit) {
        (1..25).forEach { idx ->
            println("...RUN $idx :>>>>")
            val time = measureTimeMillis {
                val results = execute().actionGet()
                results.assertHasResults()
                results.func()
            }
            println("  ${time}ms")
        }
    }

}

inline fun Pattern.takeIfMatching(text: String, minGroups: Int) =
        toRegex().matchEntire(text)?.takeIf { it.groups.size >= minGroups }