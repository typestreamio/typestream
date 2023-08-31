plugins {
    id("typestream.kotlin-conventions")
    id("typestream.version-info")
    id("com.google.cloud.tools.jib") version "3.3.2"
    application
}

application {
    mainClass.set("io.typestream.tools.Main")
}

dependencies {
    implementation(project(":libs:testing"))
    implementation(project(":libs:konfig"))
    implementation(project(":libs:version-info"))

    implementation(libs.bundles.sf4j)
    implementation(libs.bundles.kafka)
    implementation(libs.avro)
    implementation("io.confluent:kafka-avro-serializer:7.3.0")

    testImplementation(libs.bundles.testcontainers)
    testImplementation("org.testcontainers:redpanda:${libs.versions.testcontainers.get()}")
}

jib {
    to {
        image = "typestream/tools-seeder"
        tags = mutableSetOf(project.version.toString())
    }
    container {
        args = listOf("seed")
    }
}

tasks.named("classes") {
    dependsOn("createProperties")
}
