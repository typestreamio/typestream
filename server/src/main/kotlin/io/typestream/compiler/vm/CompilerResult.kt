package io.typestream.compiler.vm

import io.typestream.compiler.Program


data class CompilerResult(val program: Program, val errors: List<String>)
