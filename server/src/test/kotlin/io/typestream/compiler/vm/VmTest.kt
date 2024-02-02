package io.typestream.compiler.vm

import io.typestream.config.testing.testConfig
import io.typestream.filesystem.FileSystem
import io.typestream.scheduler.Scheduler
import io.typestream.testing.TestKafka
import io.typestream.testing.model.SmokeType
import io.typestream.testing.until
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
class VmTest {
    @Container
    private val testKafka = TestKafka()
    private val testDispatcher = Dispatchers.IO
    private lateinit var fileSystem: FileSystem
    private lateinit var session: Session
    private lateinit var scheduler: Scheduler
    private lateinit var vm: Vm

    @BeforeEach
    fun beforeEach() {
        scheduler = Scheduler(false, testDispatcher)
        val testConfig = testConfig(testKafka)
        fileSystem = FileSystem(testConfig, testDispatcher)
        session = Session(fileSystem, scheduler, Env(testConfig))
        vm = Vm(fileSystem, scheduler)
    }

    @Test
    fun `avro smokeType`(): Unit = runBlocking {
        scheduler.use {
            val smokeType = SmokeType()
            launch(testDispatcher) {
                scheduler.start()
            }
            testKafka.produceRecords("smoke-type", "avro", smokeType)

            fileSystem.refresh()

            val vmResult = vm.run("cat /dev/kafka/local/topics/smoke-type", session)

            until { scheduler.ps().first { it.contains(vmResult.program.id) } }

            scheduler.jobOutput(vmResult.program.id).collect { output ->
                val json = Json.parseToJsonElement(output).jsonObject
                val avroSmokeType = smokeType.toAvro()

                assertThat(json["booleanField"].toString().toBoolean()).isEqualTo(avroSmokeType.booleanField)
                assertThat(json["doubleField"].toString().toDouble()).isEqualTo(avroSmokeType.doubleField)
                assertThat(json["floatField"].toString().toFloat()).isEqualTo(avroSmokeType.floatField)
                assertThat(json["intField"].toString().toInt()).isEqualTo(avroSmokeType.intField)
                assertThat(json["longField"].toString().toLong()).isEqualTo(avroSmokeType.longField)
                assertThat(json["stringField"]?.jsonPrimitive?.content).isEqualTo(avroSmokeType.stringField)

                assertThat(json["arrayField"]?.jsonArray?.size).isEqualTo(avroSmokeType.arrayField.size)
                assertThat(json["arrayField"]?.jsonArray?.map { it.jsonPrimitive.content })
                    .isEqualTo(avroSmokeType.arrayField.map { it.toString() })

                assertThat(json["enumField"]?.jsonPrimitive?.content).isEqualTo(avroSmokeType.enumField.toString())

                assertThat(json["mapField"]?.jsonObject?.size).isEqualTo(avroSmokeType.mapField.size)
                val mapValue = json["mapField"]?.jsonObject?.get("key")?.jsonPrimitive?.content
                assertThat(mapValue).isEqualTo("value")
                assertThat(mapValue).isEqualTo(avroSmokeType.mapField["key"].toString())

                assertThat(json["recordField"]?.jsonObject?.size).isEqualTo(2)
                assertThat(json["recordField"]?.jsonObject?.get("nestedInt")?.jsonPrimitive?.content)
                    .isEqualTo(avroSmokeType.recordField.nestedInt.toString())
                assertThat(json["recordField"]?.jsonObject?.get("nestedString")?.jsonPrimitive?.content).isEqualTo(
                    avroSmokeType.recordField.nestedString
                )

                assertThat(json["dateField"]?.jsonPrimitive?.content).isEqualTo(avroSmokeType.dateField.toString())
                assertThat(json["decimalField"]?.jsonPrimitive?.content).isEqualTo(avroSmokeType.decimalField.toString())
                assertThat(json["localTimestampMicrosField"]?.jsonPrimitive?.content)
                    .isEqualTo(avroSmokeType.localTimestampMicrosField.toString())
                assertThat(json["localTimestampMillisField"]?.jsonPrimitive?.content).isEqualTo(
                    avroSmokeType.localTimestampMillisField.toString()
                )
                assertThat(json["timeMicrosField"]?.jsonPrimitive?.content).isEqualTo(
                    avroSmokeType.timeMicrosField.toString()
                )
                assertThat(json["timeMillisField"]?.jsonPrimitive?.content).isEqualTo(
                    avroSmokeType.timeMillisField.toString()
                )
                assertThat(json["timestampMicrosField"]?.jsonPrimitive?.content).isEqualTo(
                    avroSmokeType.timestampMicrosField.toString()
                )
                assertThat(json["timestampMillisField"]?.jsonPrimitive?.content).isEqualTo(
                    avroSmokeType.timestampMillisField.toString()
                )
                assertThat(json["uuidField"]?.jsonPrimitive?.content).isEqualTo(avroSmokeType.uuidField.toString())
                assertThat(json["optionalField"]?.jsonPrimitive?.content).isEqualTo(avroSmokeType.optionalField)

                scheduler.close()
            }
        }
    }
}
