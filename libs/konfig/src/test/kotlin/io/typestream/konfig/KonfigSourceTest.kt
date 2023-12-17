package io.typestream.konfig

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class KonfigSourceTest {

    val defaultConfig = """
        server.host=localhost
        server.port=4242
        server.protocol=https
        db.endpoint=http://db.local:5432
    """.trimIndent().toByteArray().inputStream()

    @KonfigSource(prefix = "server")
    data class ServerConfig(val host: String, val port: Int, val protocol: String = "http")

    @KonfigSource(prefix = "server")
    data class ServerConfigWithNullables(val host: String, val port: Int, val protocol: String? = null)

    @KonfigSource(prefix = "db")
    data class DbConfig(val endpoint: String)

    @KonfigSource
    data class Config(val serverHost: String, val serverPort: Int)

    @KonfigSource
    data class NestedConfig(val db: DbConfig, val server: ServerConfig)

    @KonfigSource
    data class NestedConfigWithNullables(val db: DbConfig, val server: ServerConfig? = null)

    @KonfigSource(prefix = "nested")
    data class NestedWithPrefix(val db: DbConfig, val server: ServerConfig)

    @Test
    fun `loads a simple config`() {
        class App(konfig: Konfig) {
            val serverConfig by konfig.inject<ServerConfig>()
        }

        val konfig = Konfig(defaultConfig)
        val app = App(konfig)

        assertThat(app.serverConfig).extracting("host", "port", "protocol").containsExactly("localhost", 4242, "https")
    }

    @Test
    fun `loads a simple config without prefix`() {
        class App(konfig: Konfig) {
            val config by konfig.inject<Config>()
        }

        val konfig = Konfig(defaultConfig)
        val app = App(konfig)

        assertThat(app.config).extracting("serverHost", "serverPort").containsExactly("localhost", 4242)
    }

    @Test
    fun `loads a simple config with nullables`() {
        class App(konfig: Konfig) {
            val config by konfig.inject<ServerConfigWithNullables>()
        }

        val konfig = Konfig("""
        server.host=localhost
        server.port=4242
        db.endpoint=http://db.local:5432
    """.trimIndent().toByteArray().inputStream())
        val app = App(konfig)

        assertThat(app.config).extracting("host", "port", "protocol").containsExactly("localhost", 4242, null)
    }

    @Nested
    inner class NestedTest {
        @Test
        fun `loads a nested config`() {
            class App(konfig: Konfig) {
                val config by konfig.inject<NestedConfig>()
            }

            val konfig = Konfig(defaultConfig)
            val app = App(konfig)

            assertThat(app.config)
                .extracting("db.endpoint", "server.host", "server.port")
                .containsExactly("http://db.local:5432", "localhost", 4242)
        }

        @Test
        fun `loads a nested config with prefix`() {
            class App(konfig: Konfig) {
                val config by konfig.inject<NestedWithPrefix>()
            }

            val konfig = Konfig(
                """
        nested.server.host=localhost
        nested.server.port=4242
        nested.db.endpoint=http://db.local:5432
        """.trimIndent().toByteArray().inputStream()
            )
            val app = App(konfig)

            assertThat(app.config)
                .extracting("db.endpoint", "server.host", "server.port")
                .containsExactly("http://db.local:5432", "localhost", 4242)
        }

        @Test
        fun `loads a nested config with nullables`() {
            class App(konfig: Konfig) {
                val config by konfig.inject<NestedConfigWithNullables>()
            }

            val konfig = Konfig("db.endpoint=http://db.local:5432".trimIndent().toByteArray().inputStream())
            val app = App(konfig)

            assertThat(app.config)
                .extracting("db.endpoint").isEqualTo("http://db.local:5432")
        }
    }

    @Test
    fun `ignores comments`() {
        class App(konfig: Konfig) {
            val config by konfig.inject<ServerConfig>()
        }

        val konfig = Konfig("""
            server.host=localhost
            server.port=4242
            # db.endpoint=http://db.local:5432
        """.trimIndent().toByteArray().inputStream())

        val app = App(konfig)

        assertThat(app.config).extracting("host", "port").containsExactly("localhost", 4242)
    }

}
