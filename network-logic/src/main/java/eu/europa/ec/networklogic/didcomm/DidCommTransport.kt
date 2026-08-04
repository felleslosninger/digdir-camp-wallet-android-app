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

package eu.europa.ec.networklogic.didcomm

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType

/**
 * Raw transport for DIDComm messages. Deliberately dumb: it doesn't know
 * about mediate-request, pickup, or any protocol semantics - those live in
 * didcomm-logic. This module's only job is "POST this encrypted string
 * somewhere, hand back whatever came back, or null if the server had
 * nothing to say" (matching the mediator's 202-with-no-body responses for
 * forward/messages-received - see mediator/routes.py's inbox() on the
 * server side, which returns 202 for exactly those two message types).
 */
interface DidCommTransport {
    /**
     * @param endpoint the DID Document's serviceEndpoint (or a resolved
     *                 PackEncryptedResult.serviceMetadata.serviceEndpoint)
     * @param packedMessage the encrypted DIDComm message as a JSON string
     * @return the raw response body, or null if the server returned 202
     *         (no DIDComm response - this is the normal, expected outcome
     *         for `forward` and `messages-received`, not an error)
     */
    suspend fun post(endpoint: String, packedMessage: String): String?
}

class KtorDidCommTransport(private val httpClient: HttpClient) : DidCommTransport {

    override suspend fun post(endpoint: String, packedMessage: String): String? {
        val response = httpClient.post(endpoint) {
            contentType(ContentType.parse("application/didcomm-encrypted+json"))
            setBody(packedMessage)
        }
        return if (response.status == HttpStatusCode.Accepted) {
            null
        } else {
            response.bodyAsText()
        }
    }
}
