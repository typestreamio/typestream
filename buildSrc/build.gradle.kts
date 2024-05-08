plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.24")
    implementation("org.jetbrains.kotlin:kotlin-serialization:1.9.24")
    implementation("com.adarshr:gradle-test-logger-plugin:4.0.0")
    implementation("org.jetbrains.kotlinx:kover:0.6.1")
}
