package io.typestream.compiler.types

import io.typestream.compiler.types.schema.Schema
import io.typestream.filesystem.FileSystem
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Tests for TypeRules to ensure type transformations are correct.
 * These rules are used across all compilation paths (text, graph, validation).
 */
internal class TypeRulesTest {

  private val ratingsSchema = Schema.Struct(
    listOf(
      Schema.Field("user_id", Schema.Int(0)),
      Schema.Field("book_id", Schema.Int(0)),
      Schema.Field("rating", Schema.Int(0))
    )
  )

  private val booksSchema = Schema.Struct(
    listOf(
      Schema.Field("book_id", Schema.Int(0)),
      Schema.Field("title", Schema.String("")),
      Schema.Field("author", Schema.String(""))
    )
  )

  private val ratingsStream = DataStream("/dev/kafka/local/topics/ratings", ratingsSchema)
  private val booksStream = DataStream("/dev/kafka/local/topics/books", booksSchema)

  @Nested
  inner class InferStreamSource {

    @Test
    fun `queries catalog for existing topic`() {
      val catalog = mockk<FileSystem>()
      every { catalog.findDataStream("/dev/kafka/local/topics/ratings") } returns ratingsStream

      val result = TypeRules.inferStreamSource("/dev/kafka/local/topics/ratings", catalog)

      assertThat(result).isEqualTo(ratingsStream)
    }

    @Test
    fun `throws error for non-existent topic`() {
      val catalog = mockk<FileSystem>()
      every { catalog.findDataStream("/dev/kafka/local/topics/unknown") } returns null

      val exception = assertThrows<IllegalStateException> {
        TypeRules.inferStreamSource("/dev/kafka/local/topics/unknown", catalog)
      }

      assertThat(exception.message).contains("No DataStream for path")
    }
  }

  @Nested
  inner class InferFilter {

    @Test
    fun `returns input schema unchanged (pass-through)`() {
      val result = TypeRules.inferFilter(ratingsStream)

      assertThat(result).isEqualTo(ratingsStream)
      assertThat(result.path).isEqualTo("/dev/kafka/local/topics/ratings")
      assertThat(result.schema).isEqualTo(ratingsSchema)
    }
  }

  @Nested
  inner class InferMap {

    @Test
    fun `returns input schema unchanged (stub implementation)`() {
      val result = TypeRules.inferMap(ratingsStream)

      assertThat(result).isEqualTo(ratingsStream)
      assertThat(result.path).isEqualTo("/dev/kafka/local/topics/ratings")
      assertThat(result.schema).isEqualTo(ratingsSchema)
    }
  }

  @Nested
  inner class InferJoin {

    @Test
    fun `merges left and right schemas`() {
      val result = TypeRules.inferJoin(ratingsStream, booksStream)

      // Result should have fields from both schemas
      assertThat(result.schema).isInstanceOf(Schema.Struct::class.java)
      val struct = result.schema as Schema.Struct

      // Check that fields from both streams are present
      val fieldNames = struct.value.map { it.name }
      assertThat(fieldNames).contains("user_id", "rating", "book_id", "title", "author")
    }

    @Test
    fun `uses merge path convention`() {
      val result = TypeRules.inferJoin(ratingsStream, booksStream)

      // DataStream.merge() combines paths
      assertThat(result.path).isEqualTo("/dev/kafka/local/topics/ratings_books")
    }
  }

  @Nested
  inner class InferGroup {

    @Test
    fun `returns input schema unchanged (pass-through)`() {
      val result = TypeRules.inferGroup(ratingsStream)

      assertThat(result).isEqualTo(ratingsStream)
      assertThat(result.path).isEqualTo("/dev/kafka/local/topics/ratings")
      assertThat(result.schema).isEqualTo(ratingsSchema)
    }
  }

  @Nested
  inner class InferCount {

    @Test
    fun `returns input schema unchanged (pass-through)`() {
      val result = TypeRules.inferCount(ratingsStream)

      assertThat(result).isEqualTo(ratingsStream)
      assertThat(result.path).isEqualTo("/dev/kafka/local/topics/ratings")
      assertThat(result.schema).isEqualTo(ratingsSchema)
    }
  }

  @Nested
  inner class InferEach {

    @Test
    fun `returns input schema unchanged (pass-through)`() {
      val result = TypeRules.inferEach(ratingsStream)

      assertThat(result).isEqualTo(ratingsStream)
      assertThat(result.path).isEqualTo("/dev/kafka/local/topics/ratings")
      assertThat(result.schema).isEqualTo(ratingsSchema)
    }
  }

  @Nested
  inner class InferSink {

    @Test
    fun `copies input schema with new path`() {
      val targetPath = "/dev/kafka/local/topics/high_ratings"
      val result = TypeRules.inferSink(ratingsStream, targetPath)

      // Schema should be same as input
      assertThat(result.schema).isEqualTo(ratingsSchema)

      // Path should be updated to target
      assertThat(result.path).isEqualTo(targetPath)
    }

    @Test
    fun `preserves all schema fields`() {
      val targetPath = "/dev/kafka/local/topics/output"
      val result = TypeRules.inferSink(ratingsStream, targetPath)

      // Verify all fields are preserved
      val struct = result.schema as Schema.Struct
      val fieldNames = struct.value.map { it.name }
      assertThat(fieldNames).containsExactly("user_id", "book_id", "rating")
    }
  }

  @Nested
  inner class InferShellSource {

    @Test
    fun `returns first data stream`() {
      val streams = listOf(ratingsStream, booksStream)
      val result = TypeRules.inferShellSource(streams)

      assertThat(result).isEqualTo(ratingsStream)
    }

    @Test
    fun `throws error for empty list`() {
      val exception = assertThrows<IllegalStateException> {
        TypeRules.inferShellSource(emptyList())
      }

      assertThat(exception.message).contains("ShellSource has no data streams")
    }
  }

  @Nested
  inner class InferNoOp {

    @Test
    fun `returns input schema unchanged (pass-through)`() {
      val result = TypeRules.inferNoOp(ratingsStream)

      assertThat(result).isEqualTo(ratingsStream)
      assertThat(result.path).isEqualTo("/dev/kafka/local/topics/ratings")
      assertThat(result.schema).isEqualTo(ratingsSchema)
    }
  }
}
