package io.typestream.kafka.schemaregistry

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.apache.avro.Schema

private const val CONTENT_TYPE = "application/vnd.schemaregistry.v1+json"

private val okHttpClient = OkHttpClient()

class SchemaRegistryClient(private val baseUrl: String) {

    fun subjects() = Json.decodeFromString<List<String>>(fetch("/subjects"))
        .filter { s -> s.endsWith("-value") }
        .map { name ->
            Json.decodeFromString<Subject>(fetch("/subjects/$name/versions/latest"))
        }.associateBy(Subject::subject)

    fun register(topic: String, schema: Schema): Int {
        val registerSchemaResponse = Json.decodeFromString<RegisterSchemaResponse>(
            post(
                "/subjects/$topic-value/versions",
                Json.encodeToString(RegisterSchemaRequest(schema.toString()))
            )
        )

        return registerSchemaResponse.id
    }

    private fun fetch(path: String): String {
        val request = Request.Builder()
            .header("Accept", CONTENT_TYPE)
            .url("$baseUrl$path")
            .build()

        return okHttpClient.newCall(request).execute().body?.string() ?: error("no body")
    }

    private fun post(path: String, body: String): String {
        val request = Request.Builder()
            .header("Accept", CONTENT_TYPE)
            .url("$baseUrl$path")
            .post(body.toRequestBody(CONTENT_TYPE.toMediaType()))
            .build()

        return okHttpClient.newCall(request).execute().body?.string() ?: error("no body")
    }

}
