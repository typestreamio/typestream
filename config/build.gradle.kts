plugins {
    id("typestream.kotlin-conventions")
}

dependencies {
    implementation(libs.test.containers.redpanda)
    implementation(libs.bundles.sf4j)
    implementation("net.peanuuutz.tomlkt:tomlkt:0.3.7")

    testImplementation(libs.mockk)
}
