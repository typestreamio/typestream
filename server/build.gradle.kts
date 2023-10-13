plugins {
    id("typestream.kotlin-conventions")
    id("typestream.version-info")
    id("com.google.cloud.tools.jib") version "3.4.0"
    application
}

repositories {
    mavenCentral()
    maven("https://packages.confluent.io/maven/")
}

application {
    mainClass.set("io.typestream.Main")
}

dependencies {
    implementation(project(":libs:k8s-client"))
    implementation(project(":libs:konfig"))
    implementation(project(":libs:option"))
    implementation(project(":libs:version-info"))
    implementation(project(":stub"))

    implementation(libs.avro)
    implementation(libs.bundles.kafka)
    implementation(libs.bundles.sf4j)
    runtimeOnly(libs.grpc.netty)
    implementation(libs.grpc.services)
    implementation(libs.okhttp)

    testImplementation(project(":libs:testing"))
    testImplementation(libs.bundles.testcontainers)
    testImplementation(libs.test.containers.redpanda)
    testImplementation(libs.grpc.testing)
}

jib {
    to {
        image = "typestream/server"
        tags = mutableSetOf(project.version.toString())
    }
}

tasks.named("classes") {
    dependsOn("createProperties")
}
