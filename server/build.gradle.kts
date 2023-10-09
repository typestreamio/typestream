plugins {
    id("typestream.kotlin-conventions")
    id("typestream.version-info")
    id("com.google.cloud.tools.jib") version "3.4.0"
    application
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

    implementation(libs.bundles.kafka)
    implementation(libs.bundles.sf4j)
    implementation(libs.avro)

    implementation("com.squareup.okhttp3:okhttp:4.10.0")

    runtimeOnly(libs.grpc.netty)
    implementation("io.grpc:grpc-services:${libs.versions.grpc.get()}")

    testImplementation(project(":libs:testing"))
    testImplementation(libs.bundles.testcontainers)
    testImplementation("org.testcontainers:redpanda:${libs.versions.testcontainers.get()}")
    testImplementation("io.grpc:grpc-testing:${libs.versions.grpc.get()}")
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
