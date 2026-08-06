package com.glomopay.sdk

public enum class ConnectionErrorType {
    NO_INTERNET,
    DNS_FAILURE,
    TIMEOUT,
    SSL_ERROR,
    HTTP_CLIENT_ERROR,
    HTTP_SERVER_ERROR,
    WEB_RESOURCE_ERROR,
    UNKNOWN,
}

public data class ConnectionError public constructor(
    public val type: ConnectionErrorType,
    public val message: String,
    public val statusCode: Int? = null,
    public val failedUrl: String? = null,
    public val errorCode: Int? = null,
    public val shouldAutoClose: Boolean = true,
) {
    public val isRecoverable: Boolean
        get() = type == ConnectionErrorType.NO_INTERNET ||
            type == ConnectionErrorType.TIMEOUT ||
            type == ConnectionErrorType.HTTP_SERVER_ERROR

    public companion object {
        public fun fromWebResourceError(
            description: String,
            errorCode: Int,
            failedUrl: String? = null,
        ): ConnectionError {
            val type = when {
                errorCode == -2 || errorCode == -6 -> ConnectionErrorType.NO_INTERNET
                errorCode == -105 || errorCode == -137 -> ConnectionErrorType.DNS_FAILURE
                errorCode == -7 || errorCode == -118 -> ConnectionErrorType.TIMEOUT
                errorCode in -299..-200 -> ConnectionErrorType.SSL_ERROR
                errorCode in 400..499 -> ConnectionErrorType.HTTP_CLIENT_ERROR
                errorCode in 500..599 -> ConnectionErrorType.HTTP_SERVER_ERROR
                else -> ConnectionErrorType.WEB_RESOURCE_ERROR
            }
            return ConnectionError(
                type = type,
                message = description,
                errorCode = errorCode,
                failedUrl = failedUrl,
                statusCode = errorCode.takeIf { it in 100..599 },
                shouldAutoClose = if (type == ConnectionErrorType.WEB_RESOURCE_ERROR) errorCode < 0 else true,
            )
        }

        public fun fromHttpStatus(statusCode: Int, failedUrl: String? = null): ConnectionError {
            val type = when {
                statusCode in 400..499 -> ConnectionErrorType.HTTP_CLIENT_ERROR
                statusCode in 500..599 -> ConnectionErrorType.HTTP_SERVER_ERROR
                else -> ConnectionErrorType.UNKNOWN
            }
            return ConnectionError(
                type = type,
                message = httpErrorMessage(statusCode),
                statusCode = statusCode,
                failedUrl = failedUrl,
            )
        }

        private fun httpErrorMessage(statusCode: Int): String = when (statusCode) {
            400 -> "Bad Request"
            401 -> "Unauthorized"
            403 -> "Forbidden"
            404 -> "Page Not Found"
            408 -> "Request Timeout"
            429 -> "Too Many Requests"
            500 -> "Internal Server Error"
            502 -> "Bad Gateway"
            503 -> "Service Unavailable"
            504 -> "Gateway Timeout"
            else -> "HTTP Error $statusCode"
        }
    }
}
