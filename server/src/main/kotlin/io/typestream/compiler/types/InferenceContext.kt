package io.typestream.compiler.types

/**
 * Result of schema inference for a node.
 * Contains both the output DataStream and the encoding.
 */
data class InferenceResult(
    val dataStream: DataStream,
    val encoding: Encoding
)

/**
 * Context provided to nodes during schema inference.
 * Allows nodes to look up external resources (like catalog entries) when needed.
 *
 * Only StreamSource needs catalog access - other nodes just transform their input.
 * This interface keeps the Node API clean while allowing external dependencies when needed.
 */
interface InferenceContext {
    /**
     * Look up a DataStream by path from the catalog.
     * Used by StreamSource to resolve topic schemas from the Schema Registry.
     *
     * @param path The topic path (e.g., "/dev/kafka/local/topics/ratings")
     * @return DataStream with schema from catalog
     * @throws IllegalStateException if topic doesn't exist or has no schema
     */
    fun lookupDataStream(path: String): DataStream

    /**
     * Look up the encoding for a path from the catalog.
     * Used by StreamSource to determine the encoding (AVRO, JSON, etc.) for a topic.
     *
     * @param path The topic path
     * @return The encoding for the topic
     */
    fun lookupEncoding(path: String): Encoding
}
