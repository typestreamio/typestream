package io.typestream.compiler.parser

class ParseError(message: String, val line: Int, val column: Int) : RuntimeException(message)
