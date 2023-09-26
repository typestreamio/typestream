package io.typestream.compiler.vm

import io.typestream.compiler.Program

data class VmResult(val program: Program, val programOutput: ProgramOutput)
