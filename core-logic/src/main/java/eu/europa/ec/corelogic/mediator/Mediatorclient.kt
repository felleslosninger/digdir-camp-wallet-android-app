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

package eu.europa.ec.corelogic.mediator

import eu.europa.ec.networklogic.didcomm.DidCommTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.didcommx.didcomm.DIDComm
import org.didcommx.didcomm.message.Message
import org.didcommx.didcomm.model.PackEncryptedParams
import org.didcommx.didcomm.model.UnpackParams
import java.util.UUID

private const val TYPE_MEDIATE_REQUEST = "https://didcomm.org/coordinate-mediation/1.0/mediate-request"
private const val TYPE_MEDIATE_GRANT = "https://didcomm.org/coordinate-mediation/1.0/mediate-grant"
private const val TYPE_KEYLIST_UPDATE = "https://didcomm.org/coordinate-mediation/1.0/keylist-update"
private const val TYPE_KEYLIST_UPDATE_RESPONSE = "https://didcomm.org/coordinate-mediation/1.0/keylist-update-response"

data class MediateGrant(val endpoint: String, val routingKeys: List<String>)

class MediatorClient(
    private val didComm: DIDComm,
    private val transport: DidCommTransport,
) {
    /**
     * Registers with the mediator and gets back its endpoint + routing
     * key(s). The result's [MediateGrant.endpoint] is what you publish as
     * your own DID Document's serviceEndpoint (via a normal, signed
     * POST /dids/<did>/update - not shown here, see ledger/routes.py's
     * update contract from earlier).
     */
    suspend fun mediateRequest(mediatorDid: String, myDid: String): MediateGrant =
        withContext(Dispatchers.IO) {
            val request = Message.builder(UUID.randomUUID().toString(), emptyMap(), TYPE_MEDIATE_REQUEST)
                .from(myDid)
                .to(listOf(mediatorDid))
                .build()

            val packed = didComm.packEncrypted(
                PackEncryptedParams.builder(request, mediatorDid)
                    .from(myDid)
                    .forward(false) // talking directly to the mediator - nothing to forward through
                    .build()
            )

            // serviceMetadata is populated by packEncrypted from resolving mediatorDid's
            // OWN DID Document (its "service" block) - NOT the DID string itself, which
            // is not a URL. This is how we learn the mediator's real HTTP address before
            // we've ever heard back from it.
            val endpoint = packed.serviceMetadata?.serviceEndpoint
                ?: error("mediator DID has no resolvable service endpoint")

            val responseBody = requireNotNull(transport.post(endpoint, packed.packedMessage)) {
                "mediate-request got no response - mediator endpoint must respond synchronously"
            }

            val unpacked = didComm.unpack(UnpackParams.Builder(responseBody).build())
            check(unpacked.message.type == TYPE_MEDIATE_GRANT) {
                "expected mediate-grant, got ${unpacked.message.type}"
            }

            @Suppress("UNCHECKED_CAST")
            val routingKeys = unpacked.message.body["routing_keys"] as? List<String> ?: emptyList()
            val grantedEndpoint = unpacked.message.body["endpoint"] as? String
                ?: error("mediate-grant missing endpoint")

            MediateGrant(grantedEndpoint, routingKeys)
        }

    /**
     * Registers [recipientKey] (your own DID, per the mediator's own
     * convention - see mediator/routes.py's docstring on why recipient_key
     * is always the owning DID, not a separate bare key, in this setup)
     * as something the mediator should queue forward messages for.
     */
    suspend fun keylistUpdateAdd(mediatorDid: String, myDid: String, recipientKey: String = myDid): Boolean =
        withContext(Dispatchers.IO) {
            val request = Message.builder(
                UUID.randomUUID().toString(),
                mapOf("updates" to listOf(mapOf("recipient_key" to recipientKey, "action" to "add"))),
                TYPE_KEYLIST_UPDATE
            )
                .from(myDid)
                .to(listOf(mediatorDid))
                .build()

            val packed = didComm.packEncrypted(
                PackEncryptedParams.builder(request, mediatorDid).from(myDid).forward(false).build()
            )
            val endpoint = packed.serviceMetadata?.serviceEndpoint
                ?: error("mediator DID has no resolvable service endpoint")

            val responseBody = requireNotNull(transport.post(endpoint, packed.packedMessage)) {
                "keylist-update got no response"
            }
            val unpacked = didComm.unpack(UnpackParams.Builder(responseBody).build())
            check(unpacked.message.type == TYPE_KEYLIST_UPDATE_RESPONSE) {
                "expected keylist-update-response, got ${unpacked.message.type}"
            }

            @Suppress("UNCHECKED_CAST")
            val results = unpacked.message.body["updated"] as? List<Map<String, Any?>> ?: emptyList()
            results.any { it["result"] == "success" }
        }
}