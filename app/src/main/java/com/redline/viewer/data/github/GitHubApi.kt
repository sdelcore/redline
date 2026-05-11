package com.redline.viewer.data.github

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import io.ktor.http.URLProtocol
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class GitHubApi(token: String) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val http = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
        defaultRequest {
            url {
                protocol = URLProtocol.HTTPS
                host = "api.github.com"
            }
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.Accept, "application/vnd.github+json")
            header("X-GitHub-Api-Version", "2022-11-28")
            header(HttpHeaders.UserAgent, "redline-android")
        }
    }

    suspend fun viewer(): GhUser =
        http.get("user").body()

    /**
     * Repos the authenticated user has explicit access to (owner / collaborator /
     * organization member), sorted by recent push.
     */
    suspend fun repos(perPage: Int = 50): List<GhRepo> =
        http.get("user/repos") {
            parameter("affiliation", "owner,collaborator,organization_member")
            parameter("sort", "pushed")
            parameter("direction", "desc")
            parameter("per_page", perPage)
        }.body()

    suspend fun pulls(
        owner: String,
        name: String,
        state: String = "open",
        perPage: Int = 30,
    ): List<GhPull> =
        http.get("repos/$owner/$name/pulls") {
            parameter("state", state)
            parameter("sort", "updated")
            parameter("direction", "desc")
            parameter("per_page", perPage)
        }.body()

    suspend fun pull(owner: String, name: String, number: Int): GhPull =
        http.get("repos/$owner/$name/pulls/$number").body()

    fun close() = http.close()
}
