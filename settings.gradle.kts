rootProject.name = "typestream"
include("libs:testing", "libs:k8s-client", "libs:konfig", "libs:option", "libs:version-info", "protos", "stub", "server", "tools")

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            version("grpc", "1.57.2")
            version("grpcKotlin", "1.3.1")
            version("protobuf", "3.24.2")
            version("slf4j", "2.0.7")
            version("kafka", "3.1.0")
            version("testcontainers", "1.19.0")
            version("avro", "1.11.2")
            version("rocksdbjni", "6.29.4.1")

            library("avro", "org.apache.avro", "avro").versionRef("avro")
            library("rocksdbjni", "org.rocksdb", "rocksdbjni").versionRef("rocksdbjni")
            library("kafka-clients", "org.apache.kafka", "kafka-clients").versionRef("kafka")
            library("kafka-streams", "org.apache.kafka", "kafka-streams").versionRef("kafka")
            library("grpc-netty", "io.grpc", "grpc-netty").versionRef("grpc")
            library("slf4j-simple", "org.slf4j", "slf4j-simple").versionRef("slf4j")
            library("slf4j-api", "org.slf4j", "slf4j-api").versionRef("slf4j")
            library("test-containers", "org.testcontainers", "testcontainers-bom").versionRef("testcontainers")
            library("test-containers-junit", "org.testcontainers", "junit-jupiter").versionRef("testcontainers")

            bundle("sf4j", listOf("slf4j-api", "slf4j-simple"))
            bundle("kafka", listOf("kafka-clients", "kafka-streams", "rocksdbjni"))
            bundle("testcontainers", listOf("test-containers", "test-containers-junit"))
        }
    }
}
