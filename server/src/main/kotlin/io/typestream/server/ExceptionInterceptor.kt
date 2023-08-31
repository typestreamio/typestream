package io.typestream.server

import io.grpc.ForwardingServerCall
import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.grpc.Status

// from https://github.com/grpc/grpc-kotlin/issues/141#issuecomment-726829195
class ExceptionInterceptor : ServerInterceptor {
    private class ExceptionTranslatingServerCall<ReqT, RespT>(
        delegate: ServerCall<ReqT, RespT>,
    ) : ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT>(delegate) {

        override fun close(status: Status, trailers: Metadata) {
            if (status.isOk) {
                return super.close(status, trailers)
            }
            val cause = status.cause
            var newStatus = status

            if (status.code == Status.Code.UNKNOWN) {
                val translatedStatus = when (cause) {
                    is IllegalArgumentException -> Status.INVALID_ARGUMENT
                    is IllegalStateException -> Status.FAILED_PRECONDITION
                    else -> Status.UNKNOWN
                }
                newStatus = translatedStatus.withDescription(cause?.message).withCause(cause)
            }

            super.close(newStatus, trailers)
        }
    }

    override fun <ReqT : Any, RespT : Any> interceptCall(
        call: ServerCall<ReqT, RespT>,
        headers: Metadata,
        next: ServerCallHandler<ReqT, RespT>,
    ): ServerCall.Listener<ReqT> {
        return next.startCall(ExceptionTranslatingServerCall(call), headers)
    }
}
