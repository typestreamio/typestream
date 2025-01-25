package io.typestream.server

import io.typestream.compiler.Compiler
import io.typestream.compiler.lexer.CursorPosition
import io.typestream.compiler.vm.Env
import io.typestream.compiler.vm.Session
import io.typestream.compiler.vm.Vm
import io.typestream.config.Config
import io.typestream.grpc.interactive_session_service.InteractiveSession
import io.typestream.grpc.interactive_session_service.InteractiveSession.CompleteProgramRequest
import io.typestream.grpc.interactive_session_service.InteractiveSession.GetProgramOutputRequest
import io.typestream.grpc.interactive_session_service.InteractiveSession.GetProgramOutputResponse
import io.typestream.grpc.interactive_session_service.InteractiveSession.RunProgramRequest
import io.typestream.grpc.interactive_session_service.InteractiveSession.StopSessionRequest
import io.typestream.grpc.interactive_session_service.InteractiveSessionServiceGrpcKt
import io.typestream.grpc.interactive_session_service.completeProgramResponse
import io.typestream.grpc.interactive_session_service.getProgramOutputResponse
import io.typestream.grpc.interactive_session_service.runProgramResponse
import io.typestream.grpc.interactive_session_service.startSessionResponse
import io.typestream.grpc.interactive_session_service.stopSessionResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Collections
import java.util.UUID

class InteractiveSessionService(private val config: Config, private val vm: Vm) :
    InteractiveSessionServiceGrpcKt.InteractiveSessionServiceCoroutineImplBase() {

    private val sessions = Collections.synchronizedMap(mutableMapOf<String, Session>())

    override suspend fun startSession(request: InteractiveSession.StartSessionRequest): InteractiveSession.StartSessionResponse =
        startSessionResponse {
            val sessionId = UUID.randomUUID().toString()
            this@InteractiveSessionService.sessions[sessionId] = Session(vm.fileSystem, vm.scheduler, Env(config))
            this.sessionId = sessionId
        }

    override suspend fun runProgram(request: RunProgramRequest): InteractiveSession.RunProgramResponse =
        runProgramResponse {
            val session = this@InteractiveSessionService.sessions[request.sessionId]
            requireNotNull(session) { "session ${request.sessionId} not found" }

            val vmResult = vm.run(request.source, session)
            if (vmResult.programOutput.stdErr.isBlank()) {
                session.env.addHistoryEntry(request.source)
            }

            id = vmResult.program.id
            hasMoreOutput = vmResult.program.hasMoreOutput()
            this.env["PWD"] = session.env.pwd

            this.stdOut = vmResult.programOutput.stdOut
            this.stdErr = vmResult.programOutput.stdErr
        }

    override suspend fun completeProgram(request: CompleteProgramRequest): InteractiveSession.CompleteProgramResponse = completeProgramResponse {
        val session = this@InteractiveSessionService.sessions[request.sessionId]
        requireNotNull(session) { "session ${request.sessionId} not found" }

        //right now we only support one line programs completion from the shell client
        Compiler(session).complete(request.source, CursorPosition(0, request.cursor)).forEach {
            this.value += it
        }
    }

    override fun getProgramOutput(request: GetProgramOutputRequest): Flow<GetProgramOutputResponse> {
        val session = this@InteractiveSessionService.sessions[request.sessionId]
        requireNotNull(session) { "session ${request.sessionId} not found" }

        return vm.scheduler.jobOutput(request.id).map { getProgramOutputResponse { stdOut = it } }
    }

    override suspend fun stopSession(request: StopSessionRequest): InteractiveSession.StopSessionResponse = stopSessionResponse {
        val output = StringBuilder()
        this@InteractiveSessionService.sessions[request.sessionId]?.runningPrograms?.forEach { (id, _) ->
            vm.scheduler.kill(id)
            output.appendLine("killed $id")
        }
        this@InteractiveSessionService.sessions.remove(request.sessionId)
        this.stdOut = output.toString()
        this.stdErr = ""
    }

}
