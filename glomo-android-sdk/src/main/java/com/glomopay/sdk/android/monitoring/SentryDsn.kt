package com.glomopay.sdk.android.monitoring

import java.net.URI

/**
 * A parsed Sentry DSN.
 *
 * The ingestion endpoint is derived from the DSN at runtime: scheme, host, optional port, optional
 * path prefix and project id. No host or region is hardcoded, so a DSN for any Sentry region, or a
 * self-hosted Sentry behind a path prefix, works without a code change.
 */
internal class SentryDsn private constructor(
    val value: String,
    val envelopeUrl: String,
    val publicKey: String,
    val projectId: String,
    private val secretKey: String?,
) {
    /** `X-Sentry-Auth` header value; [clientName] is reported as `sentry_client`. */
    fun authHeader(clientName: String): String = buildString {
        append("Sentry sentry_version=7")
        append(", sentry_client=").append(clientName)
        append(", sentry_key=").append(publicKey)
        secretKey?.let { append(", sentry_secret=").append(it) }
    }

    override fun toString(): String = "SentryDsn(project=$projectId)"

    companion object {
        /**
         * Returns null for anything unusable, never throws. A blank or malformed build-time DSN must
         * quietly disable error reporting rather than break a merchant's checkout.
         */
        fun parse(dsn: String?): SentryDsn? {
            val trimmed = dsn?.trim().orEmpty()
            if (trimmed.isEmpty()) return null
            return runCatching {
                val uri = URI(trimmed)
                val scheme = uri.scheme?.lowercase()
                if (scheme != "https" && scheme != "http") return null
                val host = uri.host
                if (host.isNullOrEmpty()) return null

                val credentials = uri.rawUserInfo?.split(':').orEmpty()
                val publicKey = credentials.firstOrNull().orEmpty()
                if (publicKey.isEmpty()) return null
                val secretKey = credentials.getOrNull(1)?.takeIf { it.isNotEmpty() }

                val segments = uri.rawPath.orEmpty().split('/').filter { it.isNotEmpty() }
                if (segments.isEmpty()) return null
                val projectId = segments.last()
                val prefix = segments.dropLast(1).joinToString(separator = "") { "/$it" }
                val port = if (uri.port == -1) "" else ":${uri.port}"

                SentryDsn(
                    value = trimmed,
                    envelopeUrl = "$scheme://$host$port$prefix/api/$projectId/envelope/",
                    publicKey = publicKey,
                    projectId = projectId,
                    secretKey = secretKey,
                )
            }.getOrNull()
        }
    }
}
