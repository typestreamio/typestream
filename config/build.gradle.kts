plugins {
    id("typestream.kotlin-conventions")
}

dependencies {
    implementation(project(":libs:testing"))
    implementation(project(":libs:k8s-client"))
    implementation(libs.test.containers.redpanda)
    implementation("net.peanuuutz.tomlkt:tomlkt:0.3.7")
}
