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
            return when (topic) {
                "authors" -> {
                    require(value is io.typestream.testing.avro.Author)
                    Author(value.id.toString(), value.name.toString())
                }

                "books" -> {
                    require(value is io.typestream.testing.avro.Book)
                    Book(value.id.toString(), value.title.toString(), value.authorId.toString(), value.wordCount)
                }

                "page_views" -> {
                    require(value is io.typestream.testing.avro.PageView)
                    PageView(key, value.bookId.toString(), value.ipAddress)
                }

                "ratings" -> {
                    require(value is io.typestream.testing.avro.Rating)
                    Rating(key, value.bookId.toString(), value.userId.toString(), value.rating)
                }

                "smoke-type" -> {
                    require(value is io.typestream.testing.avro.SmokeType)
                    SmokeType()
                }

                "users" -> {
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

            return when (topic) {
                "authors" -> Author(getValue("id").toString(), getValue("name").toString())

                "books" -> Book(
                    getValue("id").toString(),
                    getValue("title").toString(),
                    getValue("author_id").toString(),
                    getValue("word_count") as Int
                )

                "page_views" -> PageView(
                    key,
                    getValue("book_id").toString(),
                    getValue("ip_address").toString()
                )

                "ratings" -> Rating(
                    key,
                    getValue("book_id").toString(),
                    getValue("user_id").toString(),
                    getValue("rating") as Int
                )

                "users" -> User(getValue("id").toString(), getValue("name").toString())

                else -> error("unsupported topic: $topic")
            }
        }
    }
}
