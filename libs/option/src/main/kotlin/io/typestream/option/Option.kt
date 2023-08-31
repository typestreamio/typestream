package io.typestream.option

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class Option(vararg val names: String, val description: String)
