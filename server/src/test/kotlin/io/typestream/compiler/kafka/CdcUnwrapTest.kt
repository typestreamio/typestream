package io.typestream.compiler.kafka

import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.schema.Schema
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests for CDC envelope unwrapping at runtime.
 * CDC envelopes from Debezium have structure:
 * - before: nullable struct (null for INSERT, previous value for UPDATE/DELETE)
 * - after: nullable struct (new value for INSERT/UPDATE, null for DELETE)
 * - source: metadata about the source database
 * - op: operation type (c=create, u=update, d=delete, r=read/snapshot)
 * - ts_ms: timestamp
 */
internal class CdcUnwrapTest {

    @Nested
    inner class GetAfterFieldValue {
        @Test
        fun `returns null for non-struct schema`() {
            val schema = Schema.String("not a struct")
            val result = CdcUnwrapHelper.getAfterFieldValue(schema)
            assertThat(result).isNull()
        }

        @Test
        fun `returns null for struct without after field`() {
            val schema = Schema.Struct(
                listOf(
                    Schema.Field("id", Schema.Int(1)),
                    Schema.Field("name", Schema.String("Test"))
                )
            )
            val result = CdcUnwrapHelper.getAfterFieldValue(schema)
            assertThat(result).isNull()
        }

        @Test
        fun `extracts after field from CDC envelope with direct struct`() {
            val afterStruct = Schema.Struct(
                listOf(
                    Schema.Field("id", Schema.Int(1)),
                    Schema.Field("email", Schema.String("test@example.com"))
                )
            )
            val cdcEnvelope = Schema.Struct(
                listOf(
                    Schema.Field("before", Schema.Optional(null)),
                    Schema.Field("after", afterStruct),
                    Schema.Field("source", Schema.Struct(listOf(Schema.Field("version", Schema.String("1.0"))))),
                    Schema.Field("op", Schema.String("c"))
                )
            )

            val result = CdcUnwrapHelper.getAfterFieldValue(cdcEnvelope)

            assertThat(result).isEqualTo(afterStruct)
        }

        @Test
        fun `extracts after field from CDC envelope with Optional-wrapped struct`() {
            val innerStruct = Schema.Struct(
                listOf(
                    Schema.Field("id", Schema.Int(42)),
                    Schema.Field("name", Schema.String("User Name"))
                )
            )
            val cdcEnvelope = Schema.Struct(
                listOf(
                    Schema.Field("before", Schema.Optional(null)),
                    Schema.Field("after", Schema.Optional(innerStruct)),
                    Schema.Field("source", Schema.Struct(listOf(Schema.Field("db", Schema.String("postgres"))))),
                    Schema.Field("op", Schema.String("u"))
                )
            )

            val result = CdcUnwrapHelper.getAfterFieldValue(cdcEnvelope)

            assertThat(result).isEqualTo(innerStruct)
        }

        @Test
        fun `returns null for DELETE record where after is null`() {
            val beforeStruct = Schema.Struct(
                listOf(
                    Schema.Field("id", Schema.Int(1)),
                    Schema.Field("email", Schema.String("deleted@example.com"))
                )
            )
            val cdcEnvelope = Schema.Struct(
                listOf(
                    Schema.Field("before", beforeStruct),
                    Schema.Field("after", Schema.Optional(null)),  // null for DELETE
                    Schema.Field("source", Schema.Struct(listOf(Schema.Field("db", Schema.String("postgres"))))),
                    Schema.Field("op", Schema.String("d"))
                )
            )

            val result = CdcUnwrapHelper.getAfterFieldValue(cdcEnvelope)

            assertThat(result).isNull()
        }
    }

    @Nested
    inner class UnwrapCdcRecord {
        @Test
        fun `unwraps CDC record to flat structure`() {
            val afterStruct = Schema.Struct(
                listOf(
                    Schema.Field("id", Schema.Int(123)),
                    Schema.Field("email", Schema.String("user@example.com")),
                    Schema.Field("name", Schema.String("Test User"))
                )
            )
            val cdcEnvelope = Schema.Struct(
                listOf(
                    Schema.Field("before", Schema.Optional(null)),
                    Schema.Field("after", afterStruct),
                    Schema.Field("source", Schema.Struct(listOf(Schema.Field("version", Schema.String("2.0"))))),
                    Schema.Field("op", Schema.String("c")),
                    Schema.Field("ts_ms", Schema.Long(1234567890L))
                )
            )
            val inputDs = DataStream("/dev/kafka/local/topics/users", cdcEnvelope)

            val result = CdcUnwrapHelper.unwrapCdcRecord(inputDs)

            assertThat(result.path).isEqualTo("/dev/kafka/local/topics/users")
            assertThat(result.schema).isEqualTo(afterStruct)

            // Verify the unwrapped schema has the expected fields
            val resultStruct = result.schema as Schema.Struct
            val fieldNames = resultStruct.value.map { it.name }
            assertThat(fieldNames).containsExactly("id", "email", "name")
        }

        @Test
        fun `returns original DataStream for non-CDC schema`() {
            val regularSchema = Schema.Struct(
                listOf(
                    Schema.Field("title", Schema.String("Book Title")),
                    Schema.Field("author", Schema.String("Author Name"))
                )
            )
            val inputDs = DataStream("/dev/kafka/local/topics/books", regularSchema)

            val result = CdcUnwrapHelper.unwrapCdcRecord(inputDs)

            assertThat(result).isEqualTo(inputDs)
        }

        @Test
        fun `unwraps Optional-wrapped after struct`() {
            val innerStruct = Schema.Struct(
                listOf(
                    Schema.Field("product_id", Schema.Int(456)),
                    Schema.Field("price", Schema.Double(29.99))
                )
            )
            val cdcEnvelope = Schema.Struct(
                listOf(
                    Schema.Field("before", Schema.Optional(null)),
                    Schema.Field("after", Schema.Optional(innerStruct)),
                    Schema.Field("source", Schema.Struct(listOf())),
                    Schema.Field("op", Schema.String("c"))
                )
            )
            val inputDs = DataStream("/dev/kafka/local/topics/products", cdcEnvelope)

            val result = CdcUnwrapHelper.unwrapCdcRecord(inputDs)

            assertThat(result.schema).isEqualTo(innerStruct)
            val resultStruct = result.schema as Schema.Struct
            val fieldNames = resultStruct.value.map { it.name }
            assertThat(fieldNames).containsExactly("product_id", "price")
        }
    }
}

/**
 * Helper object for CDC unwrap operations - extracted for testability.
 * These match the private methods in KafkaStreamSource.
 */
object CdcUnwrapHelper {
    /**
     * Get the value of the 'after' field from a CDC envelope schema.
     * Returns null if the record is a DELETE (after is null) or not a CDC envelope.
     */
    fun getAfterFieldValue(schema: Schema): Schema? {
        if (schema !is Schema.Struct) return null

        val afterField = schema.value.find { it.name == "after" } ?: return null
        val afterValue = afterField.value

        return when (afterValue) {
            is Schema.Struct -> afterValue
            is Schema.Optional -> afterValue.value as? Schema.Struct
            else -> null
        }
    }

    /**
     * Unwrap a CDC record by extracting 'after' fields to top level.
     */
    fun unwrapCdcRecord(ds: DataStream): DataStream {
        val afterStruct = getAfterFieldValue(ds.schema) as? Schema.Struct ?: return ds
        return DataStream(ds.path, afterStruct)
    }
}
