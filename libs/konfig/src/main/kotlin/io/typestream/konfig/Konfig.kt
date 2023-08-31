package io.typestream.konfig

import java.io.InputStream
import java.util.Properties
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

class Konfig(source: InputStream) {
    private val props = Properties()
    private val onCamelCase = "(?<=[a-z])(?=[A-Z])".toRegex()

    init {
        props.load(source)
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

    fun <T : Any> decodeKlass(klass: KClass<T>): T {
        val klassConfig = klass.java.getAnnotation(io.typestream.konfig.KonfigSource::class.java)
        requireNotNull(klassConfig) { "${klass.java.name} is not annotated with @KonfigSource" }
        val prefix = klassConfig.prefix
        require(klass.isData) { "${klass.java.name} is not a data class" }

        return decodeParams(klass, prefix)
    }

    private fun <T : Any> decodeParams(klass: KClass<T>, prefix: String): T {
        val constructor = klass.primaryConstructor!!
        val args = arrayOfNulls<Any>(constructor.parameters.size)
        constructor.parameters.forEachIndexed { index, param ->
            when (param.type.classifier) {
                String::class -> args[index] = get(prefix, "${param.name}")
                Int::class -> args[index] = get(prefix, "${param.name}")?.toInt()
                Map::class -> {
                    val map = HashMap<String, Any>()
                    val keys = (props[prefix] as String).split(",").map { it.trim() }
                    keys.forEach { key ->
                        map[key] = decodeParams(param.type.arguments[1].type!!.classifier as KClass<*>, "$prefix.$key")
                    }

                    args[index] = map
                }

                is KClass<*> -> args[index] = decodeKlass(param.type.classifier as KClass<*>)
                else -> error("${param.type.classifier} is not supported")
            }
        }
        return constructor.call(*args)
    }

}
