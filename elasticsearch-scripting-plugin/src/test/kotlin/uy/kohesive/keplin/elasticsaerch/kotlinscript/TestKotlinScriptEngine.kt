package uy.kohesive.keplin.elasticsaerch.kotlinscript

import org.elasticsearch.client.Client
import org.elasticsearch.common.xcontent.XContentFactory
import org.elasticsearch.plugins.Plugin
import org.elasticsearch.test.ESIntegTestCase
import org.junit.Before
import uy.kohesive.keplin.elasticsearch.kotlinscript.KotlinScriptPlugin

@ESIntegTestCase.ClusterScope(transportClientRatio = 1.0, numDataNodes = 1)
class TestKotlinScriptEngine : ESIntegTestCase() {
    companion object {
        val INDEX_NAME = "test"
    }

    override fun transportClientPlugins(): MutableCollection<Class<out Plugin>> {
        return super.transportClientPlugins()
    }

    private lateinit var client: Client

    override fun nodePlugins(): Collection<Class<out Plugin>> = listOf(KotlinScriptPlugin::class.java)

    fun testSomething() {

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