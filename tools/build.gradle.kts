plugins {
    id("typestream.kotlin-conventions")
    id("typestream.version-info")
    id("com.google.cloud.tools.jib") version "3.3.2"
    application
}

repositories {
    mavenCentral()
    maven("https://packages.confluent.io/maven/")
}

application {
    mainClass.set("io.typestream.tools.Main")
}

dependencies {
    implementation(project(":libs:konfig"))
    implementation(project(":libs:k8s-client"))
    implementation(project(":libs:testing"))
    implementation(project(":libs:version-info"))

    implementation(libs.avro)
    implementation(libs.bundles.kafka)
    implementation(libs.bundles.sf4j)
    implementation(libs.kafka.avro.serializer)

    testImplementation(libs.bundles.testcontainers)
    testImplementation(libs.test.containers.redpanda)
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
