package io.typestream.konfig

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.FileInputStream

internal class KonfigSourceTest {

    @KonfigSource(prefix = "server")
    data class ServerConfig(val host: String, val port: Int, val protocol: String?)

    @KonfigSource(prefix = "db")
    data class DbConfig(val endpoint: String)

    @KonfigSource
    data class Config(val serverHost: String, val serverPort: Int)

    @KonfigSource
    data class NestedConfig(val db: DbConfig, val server: ServerConfig)

    @Test
    fun `can load a simple config`() {
        class App(konfig: io.typestream.konfig.Konfig) {
            val serverConfig by konfig.inject<ServerConfig>()
        }

        val konfig = io.typestream.konfig.Konfig(FileInputStream("src/test/resources/application.properties"))
        val app = App(konfig)

        assertThat(app.serverConfig).extracting("host", "port").containsExactly("localhost", 4242)
    }

    @Test
    fun `can load a simple config without prefix`() {
        class App(konfig: io.typestream.konfig.Konfig) {
            val config by konfig.inject<Config>()
        }

        val konfig = io.typestream.konfig.Konfig(FileInputStream("src/test/resources/application.properties"))
        val app = App(konfig)

        assertThat(app.config).extracting("serverHost", "serverPort").containsExactly("localhost", 4242)
    }

    @Test
    fun `can load a nested config`() {
        class App(konfig: io.typestream.konfig.Konfig) {
            val config by konfig.inject<NestedConfig>()
        }

        val konfig = io.typestream.konfig.Konfig(FileInputStream("src/test/resources/application.properties"))
        val app = App(konfig)

        assertThat(app.config).extracting("db.endpoint", "server.host", "server.port")
            .containsExactly("http://db.local:5432", "localhost", 4242)
    }

}
