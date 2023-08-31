package io.typestream.server

import io.typestream.compiler.Compiler
import io.typestream.compiler.lexer.CursorPosition
import io.typestream.compiler.vm.Environment
import io.typestream.compiler.vm.Session
import io.typestream.compiler.vm.Vm
import io.typestream.filesystem.FileSystem
import io.typestream.grpc.Program.CompleteProgramRequest
import io.typestream.grpc.Program.GetProgramOutputRequest
import io.typestream.grpc.Program.RunProgramRequest
import io.typestream.grpc.ProgramServiceGrpcKt
import io.typestream.grpc.completeProgramResponse
import io.typestream.grpc.getProgramOutputResponse
import io.typestream.grpc.runProgramResponse
import io.typestream.scheduler.Scheduler
import kotlinx.coroutines.flow.map

class ProgramService(fileSystem: FileSystem, scheduler: Scheduler) :
    ProgramServiceGrpcKt.ProgramServiceCoroutineImplBase() {
    private val vm = Vm(fileSystem, scheduler)

    //TODO make this a per-user session.
    // We'll introduce a startSession RPC, an eval(id, source) RPC, and a runProgram(source) RPC
    private val session = Session()

    override suspend fun runProgram(request: RunProgramRequest) = runProgramResponse {
        val (program, errors) = compile(request.source, session)

        id = program.id
        hasMoreOutput = program.hasMoreOutput()
        this.env["PWD"] = session.pwd

        if (errors.isEmpty()) {
            val programOutput = vm.run(program)
            this.stdOut = programOutput.stdOut
            this.stdErr = programOutput.stdErr
        } else {
            this.stdOut = ""
            this.stdErr = errors.joinToString("\n")
        }
    }

    override suspend fun completeProgram(request: CompleteProgramRequest) = completeProgramResponse {
        Compiler(Environment(vm.fileSystem, vm.scheduler, session)).complete(
            request.source,
            CursorPosition(
                0,
                request.cursor
            ) //right now we only support one line programs completion from the shell client
        ).forEach {
            this.value += it
        }
    }

    override fun getProgramOutput(request: GetProgramOutputRequest) =
        vm.scheduler.jobOutput(request.id).map { getProgramOutputResponse { stdOut = it } }

    private fun compile(source: String, session: Session) =
        Compiler(Environment(vm.fileSystem, vm.scheduler, session)).compile(source)
}
