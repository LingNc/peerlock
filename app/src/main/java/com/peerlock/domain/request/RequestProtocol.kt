package com.peerlock.domain.request

sealed class ProcessResult {
    data class Success(val envelope: RequestEnvelope) : ProcessResult()
    data class Error(val reason: String) : ProcessResult()
}

sealed class ResponseResult {
    data class Success(val envelope: ResponseEnvelope) : ResponseResult()
    data class Error(val reason: String) : ResponseResult()
}

interface RequestProtocol {
    suspend fun generateUnlockRequest(
        sessionId: String,
        targetPackage: String,
        requestedDuration: Int,
        durationMode: String = "cumulative",
        reason: String? = null,
        deviceInfo: DeviceInfo,
    ): String?

    suspend fun generateConfigRequest(
        sessionId: String,
        changes: List<PolicyChange>,
        reason: String? = null,
        deviceInfo: DeviceInfo,
    ): String?

    suspend fun processRequest(
        sealedBase64: String,
        peerPublicKey: ByteArray,
    ): ProcessResult

    suspend fun generateUnlockResponse(
        request: RequestEnvelope,
        approved: Boolean,
        duration: Int? = null,
        durationMode: String? = null,
    ): String?

    suspend fun generateConfigResponse(
        request: RequestEnvelope,
        approved: Boolean,
        changes: List<PolicyChange>? = null,
        rejectReason: String? = null,
    ): String?

    suspend fun processResponse(
        sealedBase64: String,
        peerPublicKey: ByteArray,
    ): ResponseResult

    suspend fun executeResponse(response: ResponseEnvelope): Boolean
}
