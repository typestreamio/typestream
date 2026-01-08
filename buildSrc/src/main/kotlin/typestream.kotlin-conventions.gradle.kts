import com.adarshr.gradle.testlogger.theme.ThemeType

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("com.adarshr.test-logger")
    id("org.jetbrains.kotlinx.kover")
}

group = "io.typestream"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.3.0")
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.8.0")

    implementation("io.github.oshai:kotlin-logging-jvm:7.0.3")

    testImplementation(kotlin("test"))
    testImplementation("org.assertj:assertj-core:3.27.3")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("nz.lae.stacksrc:stacksrc-junit5:0.6.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test-jvm:1.10.1")
}

tasks.test {
    useJUnitPlatform()
    systemProperty("junit.jupiter.extensions.autodetection.enabled", true)
    // see https://github.com/mockito/mockito/issues/3111#issuecomment-2362647742
    jvmArgs(listOf("-XX:+EnableDynamicAgentLoading", "-Xshare:off"))
}

kotlin {
    jvmToolchain(21)

    target {
        compilerOptions {
            freeCompilerArgs = listOf("-Xjsr305=strict")
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

testlogger {
    theme = ThemeType.MOCHA
    slowThreshold = 100
}
