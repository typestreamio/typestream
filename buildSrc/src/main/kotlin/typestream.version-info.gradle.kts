import java.io.ByteArrayOutputStream
import java.util.Properties

tasks.register("createProperties") {
    dependsOn("processResources")

    doLast {
        val propertiesFile = File("${buildDir}/resources/main/version-info.properties")
        val stdout = ByteArrayOutputStream()
        rootProject.exec {
            commandLine("git", "rev-parse", "--verify", "--short", "HEAD")
            standardOutput = stdout
        }
        val commitHash = stdout.toString().trim()

        propertiesFile.bufferedWriter().use { writer ->
            val properties = Properties()
            properties["version"] = if (project.version != "unspecified") {
                project.version
            } else {
                "beta"
            }
            properties["commitHash"] = commitHash
            properties.store(writer, null)
        }
    }
}
