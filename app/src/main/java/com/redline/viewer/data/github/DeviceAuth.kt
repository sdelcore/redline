package com.redline.viewer.data.github

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.accept
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import java.io.IOException
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class DeviceCode(
    val device_code: String,
    val user_code: String,
    val verification_uri: String,
    val expires_in: Int,
    val interval: Int,
)

@Serializable
private data class TokenSuccess(
    val access_token: String,
    val token_type: String? = null,
    val scope: String? = null,
)

@Serializable
private data class TokenError(
    val error: String,
    val error_description: String? = null,
    val interval: Int? = null,
)

sealed class PollResult {
    data class Success(val accessToken: String) : PollResult()
    data class Error(val code: String, val message: String) : PollResult()
}

class DeviceAuth(private val clientId: String) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val http = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
    }

    suspend fun requestCode(scope: String = "repo"): DeviceCode {
        val resp = http.post("https://github.com/login/device/code") {
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.FormUrlEncoded)
            parameter("client_id", clientId)
            parameter("scope", scope)
        }
        return json.decodeFromString(DeviceCode.serializer(), resp.bodyAsText())
    }

    /**
     * Polls the token endpoint until we have an access token, an unrecoverable
     * error, or [shouldContinue] returns false (e.g. user cancelled).
     *
     * Returns Success(token) or Error(code, message).
     */
    suspend fun poll(
        code: DeviceCode,
        onTick: (intervalSeconds: Int) -> Unit = {},
        shouldContinue: () -> Boolean = { true },
    ): PollResult {
        var interval = code.interval.coerceAtLeast(5)
        val deadline = System.currentTimeMillis() + code.expires_in * 1000L

        while (shouldContinue() && System.currentTimeMillis() < deadline) {
            delay(interval * 1000L)
            onTick(interval)

            // The user may be in another app (e.g. Chrome) while we poll. Treat
            // transient network failures as "still pending" and try again on the
            // next interval rather than aborting the whole flow.
            val resp = try {
                http.post("https://github.com/login/oauth/access_token") {
                    accept(ContentType.Application.Json)
                    contentType(ContentType.Application.FormUrlEncoded)
                    parameter("client_id", clientId)
                    parameter("device_code", code.device_code)
                    parameter("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                }
            } catch (e: IOException) {
                continue
            }

            if (resp.status != HttpStatusCode.OK) {
                return PollResult.Error("http_${resp.status.value}", resp.bodyAsText())
            }

            val body = resp.bodyAsText()
            val asSuccess = runCatching { json.decodeFromString(TokenSuccess.serializer(), body) }.getOrNull()
            if (asSuccess?.access_token?.isNotBlank() == true) {
                return PollResult.Success(asSuccess.access_token)
            }

            val asError = runCatching { json.decodeFromString(TokenError.serializer(), body) }.getOrNull()
            when (asError?.error) {
                "authorization_pending" -> { /* keep polling */ }
                "slow_down" -> interval += 5
                "expired_token", "access_denied", "incorrect_device_code", "incorrect_client_credentials" ->
                    return PollResult.Error(asError.error, asError.error_description ?: asError.error)
                else -> return PollResult.Error(asError?.error ?: "unknown", asError?.error_description ?: body)
            }
        }
        return PollResult.Error("timeout", "device code expired")
    }

    fun close() = http.close()
}
