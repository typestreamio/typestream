import java.util.Properties

//TODO It would be nice to package the task and the code it depends on in the same place. (e.g. the libs/version-info project)
tasks.register("createProperties") {
    dependsOn("processResources")

    doLast {
        val propertiesFile = File("${layout.buildDirectory.get()}/resources/main/version-info.properties")
        val gitCommand = providers.exec {
            isIgnoreExitValue = true
            commandLine("git", "rev-parse", "--verify", "--short", "HEAD")
        }
        val commitHash = gitCommand.standardOutput.asText.get().trim()

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
