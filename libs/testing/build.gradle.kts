plugins {
    id("typestream.kotlin-conventions")
    id("com.github.davidmc24.gradle.plugin.avro") version "1.5.0"
}

repositories {
    mavenCentral()
    maven("https://packages.confluent.io/maven/")
}

dependencies {
    implementation(project(":libs:konfig"))
    implementation(libs.avro)
    implementation(libs.bundles.kafka)
    implementation("org.testcontainers:redpanda:${libs.versions.testcontainers.get()}")

    implementation("io.confluent:kafka-avro-serializer:7.1.0")
}
