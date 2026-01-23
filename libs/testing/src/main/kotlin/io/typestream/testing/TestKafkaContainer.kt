package io.typestream.testing

/**
 * Singleton container for parallel test execution.
 *
 * Usage pattern (idiomatic TestContainers singleton):
 * ```kotlin
 * @Testcontainers
 * class MyTest {
 *     companion object {
 *         @Container
 *         @JvmStatic
 *         val testKafka = TestKafkaContainer.instance
 *     }
 *
 *     @Test
 *     fun `my test`() {
 *         val topic = TestKafka.uniqueTopic("users")
 *         testKafka.produceRecords(topic, "avro", User(name = "test"))
 *         // ...
 *     }
 * }
 * ```
 *
 * The container is started once and reused across all test classes,
 * significantly reducing test execution time. Tests are isolated
 * by using unique topic names via [TestKafka.uniqueTopic].
 */
object TestKafkaContainer {
    val instance: TestKafka by lazy {
        TestKafka().apply { start() }
    }
}
