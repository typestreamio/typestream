package io.typestream.konfig

import java.io.InputStream
import java.util.Properties
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.full.primaryConstructor

class Konfig(source: InputStream) {
    private val props = Properties()
    private val onCamelCase = "(?<=[a-z])(?=[A-Z])".toRegex()

    init {
        val lines = source.bufferedReader().readLines().filter { !it.startsWith("#") }

        props.load(lines.joinToString("\n").byteInputStream())
    }

    fun get(prefix: String, key: String): String? {
        return when (prefix) {
            "" -> props.getProperty(keyWithConventions(key))
            else -> props.getProperty("$prefix.$key")
        }
    }

    private fun keyWithConventions(key: String): String {
        return key.split(onCamelCase).joinToString(".") { it.lowercase() }
    }

    inline fun <reified T : Any> inject(): ReadOnlyProperty<Any, T> {
        return ReadOnlyProperty { _, _ -> decodeKlass(T::class) }
    }

    fun <T : Any> decodeKlass(klass: KClass<T>, parentPrefix: String? = null): T {
        val klassConfig = klass.java.getAnnotation(KonfigSource::class.java)
        requireNotNull(klassConfig) { "${klass.java.name} is not annotated with @KonfigSource" }
        require(klass.isData) { "${klass.java.name} is not a data class" }

        val prefix = if (parentPrefix.isNullOrBlank()) klassConfig.prefix else "$parentPrefix.${klassConfig.prefix}"

        return decodeParams(klass, prefix)
    }

    private fun <T : Any> decodeParams(klass: KClass<T>, prefix: String): T {
        val constructor = klass.primaryConstructor!!
        val args = mutableMapOf<KParameter, Any?>()
        constructor.parameters.forEach { param ->
            when (param.type.classifier) {
                String::class -> args[param] = get(prefix, "${param.name}")
                Int::class -> args[param] = get(prefix, "${param.name}")?.toInt()
                Map::class -> {
                    val map = HashMap<String, Any>()
                    val mapKey = props[prefix]
                    if (mapKey is String) {
                        val keys = mapKey.split(",").map { it.trim() }
                        keys.forEach { key ->
                            map[key] =
                                decodeParams(param.type.arguments[1].type!!.classifier as KClass<*>, "$prefix.$key")
                        }
                    }

                    args[param] = map
                }

                is KClass<*> -> {
                    val keyPrefix = "$prefix.${param.name}"
                    if (!param.type.isMarkedNullable || props.keys.count { it.toString().startsWith(keyPrefix) } > 0) {
                        args[param] = decodeKlass(param.type.classifier as KClass<*>, prefix)
                    }
                }
                else -> error("${param.type.classifier} is not supported")
            }
        }
        return constructor.callBy(args.filter { it.value != null })
    }

}
