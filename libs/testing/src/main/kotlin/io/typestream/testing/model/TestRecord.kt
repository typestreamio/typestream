package io.typestream.testing.model

import com.google.protobuf.Message
import org.apache.avro.specific.SpecificRecordBase
import org.apache.kafka.clients.producer.ProducerRecord

interface TestRecord {
    val id: String

    fun toAvro(): SpecificRecordBase
    fun toProto(): Message

    companion object {
        fun toAvroProducerRecords(
            topic: String,
            records: List<TestRecord>,
        ): List<ProducerRecord<String, SpecificRecordBase>> =
            records.map { ProducerRecord(topic, it.id, it.toAvro()) }

        fun toProtoProducerRecords(
            topic: String,
            records: List<TestRecord>,
        ): List<ProducerRecord<String, Message>> =
            records.map { ProducerRecord(topic, it.id, it.toProto()) }

        fun fromAvro(topic: String, key: String, value: SpecificRecordBase): TestRecord {
            // Use startsWith to support unique topic names (e.g., "books-abc123" matches "books")
            return when {
                topic.startsWith("authors") -> {
                    require(value is io.typestream.testing.avro.Author)
                    Author(value.id.toString(), value.name.toString())
                }

                topic.startsWith("books") -> {
                    require(value is io.typestream.testing.avro.Book)
                    Book(value.id.toString(), value.title.toString(), value.authorId.toString(), value.wordCount)
                }

                topic.startsWith("page_views") || topic.startsWith("page-views") -> {
                    require(value is io.typestream.testing.avro.PageView)
                    PageView(key, value.bookId.toString(), value.ipAddress)
                }

                topic.startsWith("ratings") -> {
                    require(value is io.typestream.testing.avro.Rating)
                    Rating(key, value.bookId.toString(), value.userId.toString(), value.rating)
                }

                topic.startsWith("smoke-type") -> {
                    require(value is io.typestream.testing.avro.SmokeType)
                    SmokeType()
                }

                topic.startsWith("users") -> {
                    require(value is io.typestream.testing.avro.User)
                    User(value.id.toString(), value.name.toString())
                }

                else -> error("unsupported topic: $topic")
            }
        }

        fun fromProto(topic: String, key: String, value: Message): TestRecord {
            val fieldsByName = value.descriptorForType.fields.associateBy { it.name }

            val getValue: (String) -> Any? = { name ->
                value.getField(fieldsByName[name])
            }

            // Use startsWith to support unique topic names (e.g., "books-abc123" matches "books")
            return when {
                topic.startsWith("authors") -> Author(getValue("id").toString(), getValue("name").toString())

                topic.startsWith("books") -> Book(
                    getValue("id").toString(),
                    getValue("title").toString(),
                    getValue("author_id").toString(),
                    getValue("word_count") as Int
                )

                topic.startsWith("page_views") || topic.startsWith("page-views") -> PageView(
                    key,
                    getValue("book_id").toString(),
                    getValue("ip_address").toString()
                )

                topic.startsWith("ratings") -> Rating(
                    key,
                    getValue("book_id").toString(),
                    getValue("user_id").toString(),
                    getValue("rating") as Int
                )

                topic.startsWith("users") -> User(getValue("id").toString(), getValue("name").toString())

                else -> error("unsupported topic: $topic")
            }
        }
    }
}
