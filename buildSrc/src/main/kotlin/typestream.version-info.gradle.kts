import java.io.ByteArrayOutputStream
import java.util.Properties

//TODO It would be nice to package the task and the code it depends on in the same place. (e.g. the libs/version-info project)
tasks.register("createProperties") {
    dependsOn("processResources")

    doLast {
        val propertiesFile = File("${layout.buildDirectory.get()}/resources/main/version-info.properties")
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
