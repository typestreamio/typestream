plugins {
    id("typestream.kotlin-conventions")

    //TODO It would be nice to package the code here and the gradle task it depends on in the same place.
    id("typestream.version-info")
    id("com.google.cloud.tools.jib") version "3.4.2"
    application
}

repositories {
    mavenCentral()
    maven("https://packages.confluent.io/maven/")
}

application {
    mainClass.set("io.typestream.MainKt")
}

tasks.named<JavaExec>("run") {
    // Inherit all environment variables from the shell
    environment(System.getenv())
}

dependencies {
    implementation(project(":config"))
    implementation(project(":libs:k8s-client"))
    implementation(project(":libs:option"))
    implementation(project(":stub"))

    implementation(libs.avro)
    implementation(libs.bundles.kafka)
    implementation(libs.bundles.sf4j)
    runtimeOnly(libs.grpc.netty)
    implementation(libs.grpc.services)
    implementation(libs.okhttp)
    implementation("com.squareup.wire:wire-schema:4.0.0")
    implementation("com.github.os72:protobuf-dynamic:1.0.1")
    implementation("com.maxmind.geoip2:geoip2:4.2.0")

    testImplementation(project(":libs:testing"))

    testImplementation(libs.bundles.testcontainers)
    testImplementation(libs.grpc.testing)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.test.containers.redpanda)
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
