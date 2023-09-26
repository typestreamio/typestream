package io.typestream.server

import io.github.oshai.kotlinlogging.KotlinLogging
import io.grpc.ForwardingServerCall.SimpleForwardingServerCall
import io.grpc.ForwardingServerCallListener.SimpleForwardingServerCallListener
import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCall.Listener
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor

class LoggerInterceptor : ServerInterceptor {
    private val logger = KotlinLogging.logger {}
    override fun <ReqT, RespT> interceptCall(
        call: ServerCall<ReqT, RespT>,
        headers: Metadata,
        next: ServerCallHandler<ReqT, RespT>
    ): Listener<ReqT> {
        val listener: ServerCall<ReqT, RespT> = object : SimpleForwardingServerCall<ReqT, RespT>(call) {
            override fun sendMessage(message: RespT) {
                logger.debug { "sending message to clients: $message" }
                super.sendMessage(message)
            }
        }
        return object : SimpleForwardingServerCallListener<ReqT>(next.startCall(listener, headers)) {
            override fun onMessage(message: ReqT) {
                logger.debug { "received message from clients: $message" }
                super.onMessage(message)
            }
        }
    }
}
