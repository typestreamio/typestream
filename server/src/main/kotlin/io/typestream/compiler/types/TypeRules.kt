package io.typestream.compiler.types

import io.typestream.compiler.types.schema.Schema
import io.typestream.filesystem.FileSystem

/**
 * TypeRules provides a single source of truth for type transformations across all compilation paths.
 *
 * This object centralizes type inference logic to ensure consistency between:
 * - Text compiler (AST → Graph<Node>)
 * - Graph compiler (Proto → Graph<Node>)
 * - Type validation (Infer.kt)
 *
 * Benefits:
 * - Single source of truth for type transformations
 * - Consistency guaranteed across compilation paths
 * - Easier testing (test rules once, trust everywhere)
 * - Easier evolution (change join semantics in one place)
 * - Self-documenting type rules
 */
object TypeRules {

  /**
   * Type inference for StreamSource nodes.
   * Queries the catalog (filesystem) for the topic's schema.
   *
   * @param path The topic path (e.g., "/dev/kafka/local/topics/ratings")
   * @param catalog The filesystem catalog to query
   * @return DataStream with schema from Schema Registry
   * @throws IllegalStateException if topic doesn't exist or has no schema
   */
  fun inferStreamSource(path: String, catalog: FileSystem): DataStream {
    return catalog.findDataStream(path)
      ?: error("No DataStream for path: $path")
  }

  /**
   * Type inference for Filter nodes.
   * Pass-through: output schema = input schema.
   * Filtering doesn't change the schema, only reduces the number of records.
   *
   * @param input The input stream schema
   * @return Same schema as input (pass-through)
   */
  fun inferFilter(input: DataStream): DataStream = input

  /**
   * Type inference for Map nodes.
   * Currently pass-through (stub implementation).
   *
   * TODO: Extract field transformations from mapper lambda.
   * For MVP, we assume Map doesn't change schema (identity function).
   * Future enhancement: analyze mapper expression to infer output schema.
   *
   * @param input The input stream schema
   * @return Currently returns input schema unchanged
   */
  fun inferMap(input: DataStream): DataStream = input

  /**
   * Type inference for Join nodes.
   * Merges left and right schemas into a combined schema.
   * Uses DataStream.merge() which combines struct fields from both streams.
   *
   * @param left The left stream schema (primary input)
   * @param right The right stream schema (join partner)
   * @return Merged schema containing fields from both streams
   */
  fun inferJoin(left: DataStream, right: DataStream): DataStream {
    return left.merge(right)
  }

  /**
   * Type inference for Group nodes.
   * Pass-through: grouping doesn't change schema.
   * Grouping only affects the key, not the record structure.
   *
   * @param input The input stream schema
   * @return Same schema as input (pass-through)
   */
  fun inferGroup(input: DataStream): DataStream = input

  /**
   * Type inference for Count nodes.
   * Pass-through: count operates on existing schema.
   * The count operation produces a KTable with the same key and a Long value.
   *
   * TODO: For more accurate typing, could return a schema with count field.
   * For now, we pass through the input schema.
   *
   * @param input The input stream schema
   * @return Same schema as input (pass-through)
   */
  fun inferCount(input: DataStream): DataStream = input

  /**
   * Type inference for Each nodes.
   * Pass-through: side effects don't change schema.
   * Each performs actions (like println) but doesn't transform data.
   *
   * @param input The input stream schema
   * @return Same schema as input (pass-through)
   */
  fun inferEach(input: DataStream): DataStream = input

  /**
   * Type inference for Sink nodes.
   * Copies input schema with new path.
   * The sink writes to a new topic with the same schema as its input.
   *
   * This is the key insight: Sink schemas are inferred from their input,
   * not looked up from the catalog (since the topic doesn't exist yet).
   *
   * @param input The input stream schema
   * @param targetPath The target topic path for the sink
   * @return Input schema with updated path
   */
  fun inferSink(input: DataStream, targetPath: String): DataStream {
    return input.copy(path = targetPath)
  }

  /**
   * Type inference for ShellSource nodes.
   * Returns the schema of the first data stream.
   * ShellSource can emit multiple streams, but we use the first one for typing.
   *
   * @param dataStreams List of data streams from the shell command
   * @return Schema of the first data stream
   * @throws IllegalStateException if no data streams provided
   */
  fun inferShellSource(dataStreams: List<DataStream>): DataStream {
    return dataStreams.firstOrNull()
      ?: error("ShellSource has no data streams")
  }

  /**
   * Type inference for NoOp nodes.
   * Pass-through: no-op doesn't change schema.
   * NoOp is used as a placeholder (e.g., root node).
   *
   * @param input The input stream schema
   * @return Same schema as input (pass-through)
   */
  fun inferNoOp(input: DataStream): DataStream = input
}
