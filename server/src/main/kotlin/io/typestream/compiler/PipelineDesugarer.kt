package io.typestream.compiler

import io.typestream.grpc.job_service.Job

data class DesugarResult(
    val graph: Job.PipelineGraph,
    val dbSinkConfigs: List<Job.DbSinkConfig>,
    val weaviateSinkConfigs: List<Job.WeaviateSinkConfig>,
    val elasticsearchSinkConfigs: List<Job.ElasticsearchSinkConfig>
)

object PipelineDesugarer {

    fun desugar(userGraph: Job.UserPipelineGraph): DesugarResult {
        val nodes = mutableListOf<Job.PipelineNode>()
        val edges = mutableListOf<Job.PipelineEdge>()
        val dbSinkConfigs = mutableListOf<Job.DbSinkConfig>()
        val weaviateSinkConfigs = mutableListOf<Job.WeaviateSinkConfig>()
        val elasticsearchSinkConfigs = mutableListOf<Job.ElasticsearchSinkConfig>()

        // Track materializedView → group node redirections for edge rewriting
        val redirectMap = mutableMapOf<String, String>()

        for (userNode in userGraph.nodesList) {
            when (userNode.nodeTypeCase) {
                Job.UserPipelineNode.NodeTypeCase.KAFKA_SOURCE -> {
                    val ks = userNode.kafkaSource
                    nodes.add(
                        Job.PipelineNode.newBuilder()
                            .setId(userNode.id)
                            .setStreamSource(
                                Job.StreamSourceNode.newBuilder()
                                    .setDataStream(Job.DataStreamProto.newBuilder().setPath(ks.topicPath))
                                    .setEncoding(ks.encoding)
                                    .setUnwrapCdc(ks.unwrapCdc)
                            )
                            .build()
                    )
                }

                Job.UserPipelineNode.NodeTypeCase.POSTGRES_SOURCE -> {
                    val ps = userNode.postgresSource
                    nodes.add(
                        Job.PipelineNode.newBuilder()
                            .setId(userNode.id)
                            .setStreamSource(
                                Job.StreamSourceNode.newBuilder()
                                    .setDataStream(Job.DataStreamProto.newBuilder().setPath(ps.topicPath))
                                    .setUnwrapCdc(true)
                            )
                            .build()
                    )
                }

                Job.UserPipelineNode.NodeTypeCase.FILTER -> {
                    val f = userNode.filter
                    nodes.add(
                        Job.PipelineNode.newBuilder()
                            .setId(userNode.id)
                            .setFilter(
                                Job.FilterNode.newBuilder()
                                    .setByKey(false)
                                    .setPredicate(Job.PredicateProto.newBuilder().setExpr(f.expression))
                            )
                            .build()
                    )
                }

                Job.UserPipelineNode.NodeTypeCase.GEO_IP -> {
                    nodes.add(
                        Job.PipelineNode.newBuilder()
                            .setId(userNode.id)
                            .setGeoIp(userNode.geoIp)
                            .build()
                    )
                }

                Job.UserPipelineNode.NodeTypeCase.TEXT_EXTRACTOR -> {
                    nodes.add(
                        Job.PipelineNode.newBuilder()
                            .setId(userNode.id)
                            .setTextExtractor(userNode.textExtractor)
                            .build()
                    )
                }

                Job.UserPipelineNode.NodeTypeCase.EMBEDDING_GENERATOR -> {
                    nodes.add(
                        Job.PipelineNode.newBuilder()
                            .setId(userNode.id)
                            .setEmbeddingGenerator(userNode.embeddingGenerator)
                            .build()
                    )
                }

                Job.UserPipelineNode.NodeTypeCase.OPEN_AI_TRANSFORMER -> {
                    nodes.add(
                        Job.PipelineNode.newBuilder()
                            .setId(userNode.id)
                            .setOpenAiTransformer(userNode.openAiTransformer)
                            .build()
                    )
                }

                Job.UserPipelineNode.NodeTypeCase.KAFKA_SINK -> {
                    val ks = userNode.kafkaSink
                    val fullPath = "/dev/kafka/local/topics/${ks.topicName}"
                    nodes.add(
                        Job.PipelineNode.newBuilder()
                            .setId(userNode.id)
                            .setSink(
                                Job.SinkNode.newBuilder()
                                    .setOutput(Job.DataStreamProto.newBuilder().setPath(fullPath))
                            )
                            .build()
                    )
                }

                Job.UserPipelineNode.NodeTypeCase.INSPECTOR -> {
                    nodes.add(
                        Job.PipelineNode.newBuilder()
                            .setId(userNode.id)
                            .setInspector(userNode.inspector)
                            .build()
                    )
                }

                Job.UserPipelineNode.NodeTypeCase.MATERIALIZED_VIEW -> {
                    val mv = userNode.materializedView
                    val groupId = "${userNode.id}-group"
                    redirectMap[userNode.id] = groupId

                    // Group node
                    nodes.add(
                        Job.PipelineNode.newBuilder()
                            .setId(groupId)
                            .setGroup(
                                Job.GroupNode.newBuilder()
                                    .setKeyMapperExpr(".${mv.groupByField}")
                            )
                            .build()
                    )

                    // Aggregation node
                    val aggBuilder = Job.PipelineNode.newBuilder().setId(userNode.id)
                    when {
                        mv.aggregationType == "count" && mv.enableWindowing && mv.windowSizeSeconds > 0 -> {
                            aggBuilder.setWindowedCount(
                                Job.WindowedCountNode.newBuilder()
                                    .setWindowSizeSeconds(mv.windowSizeSeconds)
                            )
                        }

                        mv.aggregationType == "count" -> {
                            aggBuilder.setCount(Job.CountNode.getDefaultInstance())
                        }

                        else -> {
                            aggBuilder.setReduceLatest(Job.ReduceLatestNode.getDefaultInstance())
                        }
                    }
                    nodes.add(aggBuilder.build())

                    // Internal edge: group → aggregation
                    edges.add(
                        Job.PipelineEdge.newBuilder()
                            .setFromId(groupId)
                            .setToId(userNode.id)
                            .build()
                    )
                }

                Job.UserPipelineNode.NodeTypeCase.DB_SINK -> {
                    val db = userNode.dbSink
                    val intermediateTopic = generateIntermediateTopic("jdbc-sink", userNode.id)
                    val fullPath = "/dev/kafka/local/topics/$intermediateTopic"

                    nodes.add(
                        Job.PipelineNode.newBuilder()
                            .setId(userNode.id)
                            .setSink(
                                Job.SinkNode.newBuilder()
                                    .setOutput(Job.DataStreamProto.newBuilder().setPath(fullPath))
                            )
                            .build()
                    )

                    dbSinkConfigs.add(
                        Job.DbSinkConfig.newBuilder()
                            .setNodeId(userNode.id)
                            .setConnectionId(db.connectionId)
                            .setTableName(db.tableName)
                            .setInsertMode(db.insertMode)
                            .setPrimaryKeyFields(db.primaryKeyFields)
                            .setIntermediateTopic(intermediateTopic)
                            .build()
                    )
                }

                Job.UserPipelineNode.NodeTypeCase.WEAVIATE_SINK -> {
                    val ws = userNode.weaviateSink
                    val intermediateTopic = generateIntermediateTopic("weaviate-sink", userNode.id)
                    val fullPath = "/dev/kafka/local/topics/$intermediateTopic"

                    nodes.add(
                        Job.PipelineNode.newBuilder()
                            .setId(userNode.id)
                            .setSink(
                                Job.SinkNode.newBuilder()
                                    .setOutput(Job.DataStreamProto.newBuilder().setPath(fullPath))
                            )
                            .build()
                    )

                    weaviateSinkConfigs.add(
                        Job.WeaviateSinkConfig.newBuilder()
                            .setNodeId(userNode.id)
                            .setConnectionId(ws.connectionId)
                            .setCollectionName(ws.collectionName)
                            .setDocumentIdStrategy(ws.documentIdStrategy)
                            .setDocumentIdField(ws.documentIdField)
                            .setVectorStrategy(ws.vectorStrategy)
                            .setVectorField(ws.vectorField)
                            .setTimestampField(ws.timestampField)
                            .setIntermediateTopic(intermediateTopic)
                            .build()
                    )
                }

                Job.UserPipelineNode.NodeTypeCase.ELASTICSEARCH_SINK -> {
                    val es = userNode.elasticsearchSink
                    val intermediateTopic = generateIntermediateTopic("elasticsearch-sink", userNode.id)
                    val fullPath = "/dev/kafka/local/topics/$intermediateTopic"

                    nodes.add(
                        Job.PipelineNode.newBuilder()
                            .setId(userNode.id)
                            .setSink(
                                Job.SinkNode.newBuilder()
                                    .setOutput(Job.DataStreamProto.newBuilder().setPath(fullPath))
                            )
                            .build()
                    )

                    elasticsearchSinkConfigs.add(
                        Job.ElasticsearchSinkConfig.newBuilder()
                            .setNodeId(userNode.id)
                            .setConnectionId(es.connectionId)
                            .setIndexName(es.indexName)
                            .setDocumentIdStrategy(es.documentIdStrategy)
                            .setWriteMethod(es.writeMethod)
                            .setBehaviorOnNullValues(es.behaviorOnNullValues)
                            .setIntermediateTopic(intermediateTopic)
                            .build()
                    )
                }

                else -> error("Unknown user pipeline node type: ${userNode.nodeTypeCase}")
            }
        }

        // Process user edges — redirect edges targeting materializedView to its group node
        for (edge in userGraph.edgesList) {
            val toId = redirectMap[edge.toId] ?: edge.toId
            edges.add(
                Job.PipelineEdge.newBuilder()
                    .setFromId(edge.fromId)
                    .setToId(toId)
                    .build()
            )
        }

        return DesugarResult(
            graph = Job.PipelineGraph.newBuilder()
                .addAllNodes(nodes)
                .addAllEdges(edges)
                .build(),
            dbSinkConfigs = dbSinkConfigs,
            weaviateSinkConfigs = weaviateSinkConfigs,
            elasticsearchSinkConfigs = elasticsearchSinkConfigs
        )
    }

    private fun generateIntermediateTopic(prefix: String, nodeId: String): String {
        val sanitized = nodeId.replace(Regex("[^a-zA-Z0-9]"), "-")
        return "$prefix-$sanitized-${System.currentTimeMillis()}"
    }
}
