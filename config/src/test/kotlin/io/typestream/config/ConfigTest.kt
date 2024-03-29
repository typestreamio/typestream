package io.typestream.config

import io.mockk.every
import io.mockk.mockkObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.io.path.createTempDirectory

internal class ConfigTest {

    @BeforeEach
    fun beforeEach() {
        mockkObject(VersionInfo)
        every { VersionInfo.fetch() } returns VersionInfo("beta", "n/a")
        mockkObject(SystemEnv)
    }

    @Test
    fun `loads from TYPESTREAM_CONFIG`() {
        val tempDir = createTempDirectory()

        every { SystemEnv["TYPESTREAM_SYSTEM_CONFIG_PATH"] } returns "$tempDir/typestream"

        every { SystemEnv["TYPESTREAM_CONFIG"] } returns """
            [grpc]
            port=4242
            [sources.kafka.local]
            bootstrapServers="localhost:9092"
            schemaRegistry.url="http://localhost:8081"
            fsRefreshRate=1
        """.trimIndent()

        val config = Config.fetch()

        assertThat(config).extracting(
            "sources.kafka.local.bootstrapServers",
            "sources.kafka.local.schemaRegistry.url",
            "sources.kafka.local.fsRefreshRate",
            "grpc.port",
            "k8sMode",
            "versionInfo.version",
            "versionInfo.commitHash",
            "configPath"
        ).containsExactly(
            "localhost:9092",
            "http://localhost:8081",
            1,
            4242,
            false,
            "beta",
            "n/a",
            "$tempDir/typestream"
        )

        val configFile = tempDir.resolve("typestream/typestream.toml")
        assertThat(configFile.toFile().readText()).isEqualTo(
            """
            [grpc]
            port=4242
            [sources.kafka.local]
            bootstrapServers="localhost:9092"
            schemaRegistry.url="http://localhost:8081"
            fsRefreshRate=1
        """.trimIndent()
        )
    }

    @Test
    fun `loads defaults`() {
        val tempDir = createTempDirectory()

        every { SystemEnv["TYPESTREAM_SYSTEM_CONFIG_PATH"] } returns "$tempDir/typestream"

        val config = Config.fetch()

        assertThat(config).extracting(
            "sources.kafka.local.bootstrapServers",
            "sources.kafka.local.schemaRegistry.url",
            "sources.kafka.local.fsRefreshRate",
            "grpc.port",
            "k8sMode",
            "versionInfo.version",
            "versionInfo.commitHash",
            "configPath"
        ).containsExactly(
            "example.com:9092",
            "https://example.com:8081",
            42,
            4242,
            false,
            "beta",
            "n/a",
            "$tempDir/typestream"
        )

        val configFile = tempDir.resolve("typestream/typestream.toml")
        assertThat(configFile.toFile().readText()).isEqualTo(
            """
            [grpc]
            port=4242
            [sources.kafka.local]
            bootstrapServers="example.com:9092"
            schemaRegistry.url="https://example.com:8081"
            fsRefreshRate=42
            """.trimIndent()
        )
    }


    @Nested
    inner class MountAutoConfig {
        @Test
        fun `loads mounts via auto config`() {
            val tempDir = createTempDirectory()

            tempDir.resolve("typestream").toFile().mkdir()

            tempDir.resolve("typestream/typestream.auto.toml").toFile().writeText(
                """
                # THIS FILE IS AUTOGENERATED. DO NOT EDIT
                [mounts]
                
                [mounts.random]
                
                [mounts.random.int]
                valueType = "int"
                endpoint = "/mnt/random/int"
                """.trimIndent()
            )

            every { SystemEnv["TYPESTREAM_SYSTEM_CONFIG_PATH"] } returns "$tempDir/typestream"

            val config = Config.fetch()

            assertThat(config).extracting(
                "mounts.random.int.endpoint",
                "sources.kafka.local.bootstrapServers",
                "sources.kafka.local.schemaRegistry.url",
                "sources.kafka.local.fsRefreshRate",
                "grpc.port",
                "k8sMode",
                "versionInfo.version",
                "versionInfo.commitHash",
                "configPath"
            ).containsExactly(
                "/mnt/random/int",
                "example.com:9092",
                "https://example.com:8081",
                42,
                4242,
                false,
                "beta",
                "n/a",
                "$tempDir/typestream"
            )
        }

        @Test
        fun `mounts endpoint`() {
            val tempDir = createTempDirectory()

            tempDir.resolve("typestream").toFile().mkdir()

            tempDir.resolve("typestream/typestream.auto.toml").toFile().writeText(
                """
                # THIS FILE IS AUTOGENERATED. DO NOT EDIT
                [mounts]
                
                [mounts.random]
                
                [mounts.random.int]
                valueType = "int"
                endpoint = "/mnt/random/int"
                """.trimIndent()
            )

            every { SystemEnv["TYPESTREAM_SYSTEM_CONFIG_PATH"] } returns "$tempDir/typestream"

            val config = Config.fetch()

            config.mount(
                MountsConfig(
                    random = mutableMapOf(
                        "anotherInt" to RandomConfig("anotherInt", "/mnt/random/anotherInt"),
                        "string" to RandomConfig("string", "/mnt/random/string")
                    )
                )
            )

            assertThat(config).extracting(
                "mounts.random.int.endpoint",
                "mounts.random.anotherInt.endpoint",
                "mounts.random.string.endpoint",
                "sources.kafka.local.bootstrapServers",
                "sources.kafka.local.schemaRegistry.url",
                "sources.kafka.local.fsRefreshRate",
                "grpc.port",
                "k8sMode",
                "versionInfo.version",
                "versionInfo.commitHash",
                "configPath"
            ).containsExactly(
                "/mnt/random/int",
                "/mnt/random/anotherInt",
                "/mnt/random/string",
                "example.com:9092",
                "https://example.com:8081",
                42,
                4242,
                false,
                "beta",
                "n/a",
                "$tempDir/typestream"
            )

            assertThat(tempDir.resolve("typestream/typestream.auto.toml").toFile().readText().trimIndent()).isEqualTo(
                """
                # THIS FILE IS AUTOGENERATED. DO NOT EDIT
                [mounts]
                
                [mounts.random]
                
                [mounts.random.anotherInt]
                valueType = "anotherInt"
                endpoint = "/mnt/random/anotherInt"
                
                [mounts.random.int]
                valueType = "int"
                endpoint = "/mnt/random/int"
              
                [mounts.random.string]
                valueType = "string"
                endpoint = "/mnt/random/string"
            """.trimIndent()
            )
        }
    }

    @Test
    fun `unmounts endpoints`() {
        val tempDir = createTempDirectory()

        tempDir.resolve("typestream").toFile().mkdir()

        tempDir.resolve("typestream/typestream.auto.toml").toFile().writeText(
            """
            # THIS FILE IS AUTOGENERATED. DO NOT EDIT
            [mounts]
            
            [mounts.random]
            
            [mounts.random.anotherInt]
            valueType = "int"
            endpoint = "/mnt/random/anotherInt"
            
            [mounts.random.int]
            valueType = "int"
            endpoint = "/mnt/random/int"
            """.trimIndent()
        )

        every { SystemEnv["TYPESTREAM_SYSTEM_CONFIG_PATH"] } returns "$tempDir/typestream"

        val config = Config.fetch()

        config.unmount("/mnt/random/int")

        assertThat(config.mounts.random).extracting("anotherInt")
            .isEqualTo(RandomConfig("int", "/mnt/random/anotherInt"))

        assertThat(tempDir.resolve("typestream/typestream.auto.toml").toFile().readText().trimIndent()).isEqualTo(
            """
            # THIS FILE IS AUTOGENERATED. DO NOT EDIT
            [mounts]
            
            [mounts.random]
            
            [mounts.random.anotherInt]
            valueType = "int"
            endpoint = "/mnt/random/anotherInt"
            """.trimIndent()
        )
    }
}
