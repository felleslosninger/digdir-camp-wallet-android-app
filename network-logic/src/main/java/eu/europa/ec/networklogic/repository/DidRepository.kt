/*
 * Copyright (c) 2026 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work
 * except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific language
 * governing permissions and limitations under the Licence.
 */

package eu.europa.ec.networklogic.repository


import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive



@Serializable
data class DidDocumentMetadata(
    val created: String? = null,
    val updated: String? = null,
    val versionId: String? = null,
    val previousVersionId: String? = null,
    val deactivated: Boolean = false
)

@Serializable
data class DidResponse(
    val did: String,
    val didDocument: JsonObject? = null,
    val didDocumentMetadata: DidDocumentMetadata
)

@Serializable
data class DidProof(
    val signature: String
)

@Serializable
data class DidCreateRequest(
    val didDocument: JsonObject,
    val proof: DidProof
)

@Serializable
data class DidUpdateRequest(
    val didDocument: JsonObject,
    val proof: DidProof
)

@Serializable
data class DidDeactivateRequest(
    val proof: DidProof
)

interface DidRepository {
    suspend fun mintId(baseUrl: String): Result<String>
    suspend fun create(baseUrl: String, request: DidCreateRequest): Result<DidResponse>
    suspend fun resolve(baseUrl: String, did: String): Result<DidResponse>
    suspend fun update(baseUrl: String, did: String, request: DidUpdateRequest): Result<DidResponse>
    suspend fun deactivate(baseUrl: String, did: String, request: DidDeactivateRequest): Result<DidResponse>
}

class DidRepositoryImpl(
    private val httpClient: HttpClient
) : DidRepository {

    override suspend fun mintId(baseUrl: String): Result<String> = runCatching {
        val response: JsonObject = httpClient.get("$baseUrl/dids/mint-id").body()
        response["did"]?.jsonPrimitive?.content ?: throw IllegalStateException("No DID returned")
    }

    override suspend fun create(baseUrl: String, request: DidCreateRequest): Result<DidResponse> = runCatching {
        httpClient.post("$baseUrl/dids") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    override suspend fun resolve(baseUrl: String, did: String): Result<DidResponse> = runCatching {
        httpClient.get("$baseUrl/dids/$did").body()
    }

    override suspend fun update(baseUrl: String, did: String, request: DidUpdateRequest): Result<DidResponse> = runCatching {
        httpClient.post("$baseUrl/dids/$did/update") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    override suspend fun deactivate(baseUrl: String, did: String, request: DidDeactivateRequest): Result<DidResponse> = runCatching {
        httpClient.post("$baseUrl/dids/$did/deactivate") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }
}