plugins {
    id("typestream.kotlin-conventions")
    id("com.github.davidmc24.gradle.plugin.avro") version "1.9.1"
    id("com.gradleup.shadow") version "9.0.0"
    application
}

repositories {
    mavenCentral()
    maven("https://packages.confluent.io/maven/")
}

application {
    mainClass.set("io.typestream.connectors.MainKt")
}

tasks.named<JavaExec>("run") {
    environment(System.getenv())
}

dependencies {
    implementation(libs.avro)
    implementation(libs.kafka.clients)
    implementation(libs.kafka.avro.serializer)
    implementation(libs.bundles.sf4j)
    implementation(libs.okhttp)
    implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")

    implementation("com.github.ajalt.clikt:clikt:5.0.3")

    // Web visits connector dependencies
    implementation("net.datafaker:datafaker:2.4.2")
    implementation("commons-net:commons-net:3.11.1")

    // PostgreSQL JDBC driver for file uploads connector
    implementation("org.postgresql:postgresql:42.7.4")

    // Test dependencies
    testImplementation("org.testcontainers:testcontainers:1.20.4")
    testImplementation("org.testcontainers:junit-jupiter:1.20.4")
    testImplementation("org.testcontainers:postgresql:1.20.4")
}

avro {
    setCreateSetters(false)
    setFieldVisibility("PRIVATE")
}

// Ensure Avro generation happens before Kotlin compilation
tasks.named("compileKotlin") {
    dependsOn("generateAvroJava")
}

// Add generated Avro sources to source set
sourceSets {
    main {
        java {
            srcDir(layout.buildDirectory.dir("generated-main-avro-java"))
        }
    }
}
