package io.typestream.pipeline

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

internal class PipelineFileParserTest {

    @Test
    fun `parses pipeline file with all fields`() {
        val json = """
        {
            "name": "webvisits-geo",
            "version": "2",
            "description": "Enrich web visits with GeoIP data",
            "graph": {
                "nodes": [
                    {
                        "id": "source-1",
                        "streamSource": {
                            "dataStream": { "path": "/dev/kafka/local/topics/webvisits" },
                            "encoding": "AVRO"
                        }
                    },
                    {
                        "id": "filter-1",
                        "filter": {
                            "predicate": { "expr": ".country == \"US\"" }
                        }
                    },
                    {
                        "id": "sink-1",
                        "sink": {
                            "output": { "path": "/dev/kafka/local/topics/webvisits-us" }
                        }
                    }
                ],
                "edges": [
                    { "fromId": "source-1", "toId": "filter-1" },
                    { "fromId": "filter-1", "toId": "sink-1" }
                ]
            }
        }
        """.trimIndent()

        val result = PipelineFileParser.parse(json)

        assertThat(result.metadata.name).isEqualTo("webvisits-geo")
        assertThat(result.metadata.version).isEqualTo("2")
        assertThat(result.metadata.description).isEqualTo("Enrich web visits with GeoIP data")
        assertThat(result.graph.nodesCount).isEqualTo(3)
        assertThat(result.graph.edgesCount).isEqualTo(2)

        val sourceNode = result.graph.nodesList.first { it.id == "source-1" }
        assertThat(sourceNode.hasStreamSource()).isTrue()
        assertThat(sourceNode.streamSource.dataStream.path).isEqualTo("/dev/kafka/local/topics/webvisits")

        val filterNode = result.graph.nodesList.first { it.id == "filter-1" }
        assertThat(filterNode.hasFilter()).isTrue()
        assertThat(filterNode.filter.predicate.expr).isEqualTo(".country == \"US\"")

        val sinkNode = result.graph.nodesList.first { it.id == "sink-1" }
        assertThat(sinkNode.hasSink()).isTrue()
        assertThat(sinkNode.sink.output.path).isEqualTo("/dev/kafka/local/topics/webvisits-us")
    }

    @Test
    fun `defaults version to 1 when not specified`() {
        val json = """
        {
            "name": "simple-pipeline",
            "graph": {
                "nodes": [
                    {
                        "id": "source-1",
                        "streamSource": {
                            "dataStream": { "path": "/dev/kafka/local/topics/input" }
                        }
                    }
                ],
                "edges": []
            }
        }
        """.trimIndent()

        val result = PipelineFileParser.parse(json)
        assertThat(result.metadata.version).isEqualTo("1")
        assertThat(result.metadata.description).isEmpty()
    }

    @Test
    fun `fails when name is missing`() {
        val json = """
        {
            "graph": {
                "nodes": [],
                "edges": []
            }
        }
        """.trimIndent()

        assertThatThrownBy { PipelineFileParser.parse(json) }
            .hasMessageContaining("name")
    }

    @Test
    fun `fails when graph is missing`() {
        val json = """
        {
            "name": "no-graph"
        }
        """.trimIndent()

        assertThatThrownBy { PipelineFileParser.parse(json) }
            .hasMessageContaining("graph")
    }

    @Test
    fun `parses pipeline with group and count nodes`() {
        val json = """
        {
            "name": "count-by-country",
            "graph": {
                "nodes": [
                    {
                        "id": "source-1",
                        "streamSource": {
                            "dataStream": { "path": "/dev/kafka/local/topics/visits" },
                            "encoding": "AVRO"
                        }
                    },
                    {
                        "id": "group-1",
                        "group": { "keyMapperExpr": ".country" }
                    },
                    {
                        "id": "count-1",
                        "count": {}
                    }
                ],
                "edges": [
                    { "fromId": "source-1", "toId": "group-1" },
                    { "fromId": "group-1", "toId": "count-1" }
                ]
            }
        }
        """.trimIndent()

        val result = PipelineFileParser.parse(json)

        assertThat(result.graph.nodesCount).isEqualTo(3)
        val groupNode = result.graph.nodesList.first { it.id == "group-1" }
        assertThat(groupNode.hasGroup()).isTrue()
        assertThat(groupNode.group.keyMapperExpr).isEqualTo(".country")

        val countNode = result.graph.nodesList.first { it.id == "count-1" }
        assertThat(countNode.hasCount()).isTrue()
    }

    @Test
    fun `parses pipeline with join node`() {
        val json = """
        {
            "name": "users-orders-join",
            "graph": {
                "nodes": [
                    {
                        "id": "source-1",
                        "streamSource": {
                            "dataStream": { "path": "/dev/kafka/local/topics/orders" },
                            "encoding": "AVRO"
                        }
                    },
                    {
                        "id": "join-1",
                        "join": {
                            "with": { "path": "/dev/kafka/local/topics/users" },
                            "joinType": { "byKey": true }
                        }
                    }
                ],
                "edges": [
                    { "fromId": "source-1", "toId": "join-1" }
                ]
            }
        }
        """.trimIndent()

        val result = PipelineFileParser.parse(json)

        val joinNode = result.graph.nodesList.first { it.id == "join-1" }
        assertThat(joinNode.hasJoin()).isTrue()
        assertThat(joinNode.join.with.path).isEqualTo("/dev/kafka/local/topics/users")
        assertThat(joinNode.join.joinType.byKey).isTrue()
    }

    @Test
    fun `parses pipeline with map node`() {
        val json = """
        {
            "name": "select-fields",
            "graph": {
                "nodes": [
                    {
                        "id": "source-1",
                        "streamSource": {
                            "dataStream": { "path": "/dev/kafka/local/topics/users" },
                            "encoding": "AVRO"
                        }
                    },
                    {
                        "id": "map-1",
                        "map": { "mapperExpr": "select .name .email" }
                    }
                ],
                "edges": [
                    { "fromId": "source-1", "toId": "map-1" }
                ]
            }
        }
        """.trimIndent()

        val result = PipelineFileParser.parse(json)

        val mapNode = result.graph.nodesList.first { it.id == "map-1" }
        assertThat(mapNode.hasMap()).isTrue()
        assertThat(mapNode.map.mapperExpr).isEqualTo("select .name .email")
    }
}
