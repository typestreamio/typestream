import com.google.protobuf.gradle.id

plugins {
    id("typestream.kotlin-conventions")
    id("com.github.davidmc24.gradle.plugin.avro") version "1.5.0"
    id("com.google.protobuf") version "0.9.4"
}

repositories {
    mavenCentral()
    google()
    maven("https://packages.confluent.io/maven/")
}

val protobufVersion: String = libs.versions.protobuf.get()

dependencies {
    implementation(libs.avro)
    implementation(libs.bundles.kafka)
    implementation(libs.test.containers.redpanda)
    implementation(libs.kafka.avro.serializer)
    implementation(libs.kafka.protobuf.serializer)

    api("com.google.protobuf:protobuf-java-util:$protobufVersion")
    api("com.google.protobuf:protobuf-kotlin:$protobufVersion")
}


project.protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protobufVersion"
    }
    generateProtoTasks {
        all().forEach {
            it.builtins {
                id("kotlin")
            }
        }
    }
}
