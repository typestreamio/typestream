package io.typestream.option

import kotlin.reflect.full.findAnnotations

inline fun <reified T> parseOptions(args: List<String>): Pair<T, List<String>> {
    val consumedOptionsIndexes = mutableListOf<Int>()
    val klass = T::class
    require(klass.isData) { "${klass.java.name} is not a data class" }

    val constructor = klass.constructors.first()

    val params = arrayOfNulls<Any>(constructor.parameters.size)
    constructor.parameters.forEachIndexed { index, param ->
        val annotations = param.findAnnotations(Option::class)
        require(annotations.size == 1) { "Parameter ${param.name} must have one @param:Option annotation" }

        val annotation = annotations.first()
        val foundIndex = annotation.names.map { args.indexOf(it) }.firstOrNull { it != -1 }
        if (foundIndex != null) {
            consumedOptionsIndexes.add(foundIndex)
        }
        params[index] = when (param.type.classifier) {
            String::class -> if (foundIndex != null) {
                consumedOptionsIndexes.add(foundIndex + 1)
                args[foundIndex + 1]
            } else ""

            Int::class -> if (foundIndex != null) {
                consumedOptionsIndexes.add(foundIndex + 1)
                args[foundIndex + 1].toInt()
            } else 0

            Boolean::class -> foundIndex != null
            else -> throw IllegalArgumentException("Unsupported type ${param.type.classifier}")
        }
    }

    return Pair(constructor.call(*params), args.filterIndexed { i, _ -> !consumedOptionsIndexes.contains(i) })
}
