plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.0")
    implementation("org.jetbrains.kotlin:kotlin-serialization:2.3.0")
    implementation("com.adarshr:gradle-test-logger-plugin:4.0.0")
    implementation("org.jetbrains.kotlinx:kover-gradle-plugin:0.9.4")
}
