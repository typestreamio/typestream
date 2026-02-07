rootProject.name = "typestream"

include(
    "config",
    "connectors:demo-data",
    "libs:testing",
    "libs:k8s-client",
    "libs:option",
    "protos",
    "stub",
    "server",
    "tools"
)

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            version("avro", "1.11.3")
            version("confluent", "7.1.0")
            version("grpc", "1.57.2")
            version("grpcKotlin", "1.3.1")
            version("kafka", "4.1.1")
            version("kubernetes-client", "6.9.0")
            version("okhttp", "4.12.0")
            version("mockk", "1.14.3")
            version("protobuf", "3.24.2")
            version("rocksdbjni", "6.29.4.1")
            version("slf4j", "2.0.7")
            version("testcontainers", "2.0.3")

            library("avro", "org.apache.avro", "avro").versionRef("avro")
            library("grpc-netty", "io.grpc", "grpc-netty").versionRef("grpc")
            library("grpc-services", "io.grpc", "grpc-services").versionRef("grpc")
            library("grpc-testing", "io.grpc", "grpc-testing").versionRef("grpc")
            library("kafka-clients", "org.apache.kafka", "kafka-clients").versionRef("kafka")
            library("kafka-streams", "org.apache.kafka", "kafka-streams").versionRef("kafka")
            library("kafka-avro-serializer", "io.confluent", "kafka-avro-serializer").versionRef("confluent")
            library("kafka-protobuf-serializer", "io.confluent", "kafka-protobuf-serializer").versionRef("confluent")
            library("kubernetes-client", "io.fabric8", "kubernetes-client").versionRef("kubernetes-client")
            library("mockk", "io.mockk", "mockk").versionRef("mockk")
            library("okhttp", "com.squareup.okhttp3", "okhttp").versionRef("okhttp")
            library("okhttp-mockwebserver", "com.squareup.okhttp3", "mockwebserver").versionRef("okhttp")
            library("rocksdbjni", "org.rocksdb", "rocksdbjni").versionRef("rocksdbjni")
            library("slf4j-simple", "org.slf4j", "slf4j-simple").versionRef("slf4j")
            library("slf4j-api", "org.slf4j", "slf4j-api").versionRef("slf4j")
            library("test-containers", "org.testcontainers", "testcontainers-bom").versionRef("testcontainers")
            library("test-containers-junit", "org.testcontainers", "testcontainers-junit-jupiter").versionRef("testcontainers")
            library("test-containers-redpanda", "org.testcontainers", "testcontainers-redpanda").versionRef("testcontainers")

            bundle("sf4j", listOf("slf4j-api", "slf4j-simple"))
            bundle("kafka", listOf("kafka-clients", "kafka-streams", "rocksdbjni"))
            bundle("testcontainers", listOf("test-containers", "test-containers-junit"))
        }
    }
}
