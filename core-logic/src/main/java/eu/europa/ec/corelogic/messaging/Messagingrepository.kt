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

package eu.europa.ec.corelogic.messaging

import android.util.Base64
import eu.europa.ec.networklogic.didcomm.DidCommTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.didcommx.didcomm.DIDComm
import org.didcommx.didcomm.message.Attachment
import org.didcommx.didcomm.message.Message
import org.didcommx.didcomm.model.PackEncryptedParams
import org.didcommx.didcomm.model.UnpackParams
import java.util.UUID

private const val TYPE_DELIVERY_REQUEST = "https://didcomm.org/message-pickup/4.0/delivery-request"
private const val TYPE_DELIVERY = "https://didcomm.org/message-pickup/4.0/delivery"
private const val TYPE_MESSAGES_RECEIVED = "https://didcomm.org/message-pickup/4.0/messages-received"

data class ReceivedMessage(
    val deliveryAttachmentId: String, // needed to ack via messages-received
    val from: String?,
    val type: String,
    val body: Map<String, Any?>,
)

/**
 * Sending and receiving application messages. A recipient must have already done
 * mediateRequest + keylistUpdateAdd and published a service block on their
 * own DID Document (see MediatorClient) before sendMessage will have
 * anywhere to actually deliver to.
 */
class MessagingRepository(
    private val didComm: DIDComm,
    private val transport: DidCommTransport,
) {

    /**
     * Packs and sends [body] to [toDid]. If the recipient's resolved DID
     * Document declares routingKeys (i.e. they use a mediator), packEncrypted
     * automatically wraps this as a `forward` message addressed to their
     * mediator - this function doesn't need to know or care whether that's
     * happening; see PackEncryptedParams.Builder's forward parameter
     * (true by default) in the didcomm-jvm source.
     */
    suspend fun sendMessage(toDid: String, fromDid: String, type: String, body: Map<String, Any?>) {
        withContext(Dispatchers.IO) {
            val message = Message.builder(UUID.randomUUID().toString(), body, type)
                .from(fromDid)
                .to(listOf(toDid))
                .build()

            val packed = didComm.packEncrypted(
                PackEncryptedParams.builder(message, toDid).from(fromDid).build() // forward defaults to true
            )

            // Resolved from toDid's OWN service block - this is the recipient's
            // mediator endpoint if they have one, same mechanism as MediatorClient.
            val endpoint = packed.serviceMetadata?.serviceEndpoint
                ?: error("recipient DID has no resolvable service endpoint")

            transport.post(endpoint, packed.packedMessage)
            // A `forward` message always gets a 202/no-body response (see
            // mediator/routes.py's inbox()) - nothing further to unpack here.
        }
    }

    /**
     * One pickup round: ask the mediator what's queued for [myDid], unpack
     * each message (still doubly-encrypted inside the delivery envelope, so
     * this does a SECOND unpack per attachment - once for the delivery
     * message itself, once for each inner message it carried), then
     * acknowledge receipt so the mediator clears its queue.
     *
     * This is a single request/response round trip, not a loop - call this
     * periodically (WorkManager) or in response to an FCM wake-up push;
     * see the earlier discussion on background delivery strategies for why
     * a phone can't just hold this open indefinitely.
     */
    suspend fun receiveMessages(mediatorDid: String, myDid: String, limit: Int = 10): List<ReceivedMessage> =
        withContext(Dispatchers.IO) {
            val request = Message.builder(
                UUID.randomUUID().toString(),
                mapOf("message_count_limit" to limit),
                TYPE_DELIVERY_REQUEST
            )
                .from(myDid)
                .to(listOf(mediatorDid))
                .customHeader("return_route", "all") // required for the mediator to reply synchronously
                .build()

            val packed = didComm.packEncrypted(
                PackEncryptedParams.builder(request, mediatorDid).from(myDid).forward(false).build()
            )
            val endpoint = packed.serviceMetadata?.serviceEndpoint
                ?: error("mediator DID has no resolvable service endpoint")

            val responseBody = requireNotNull(transport.post(endpoint, packed.packedMessage)) {
                "delivery-request got no response - the mediator should always reply, even with an empty delivery"
            }

            val delivery = didComm.unpack(UnpackParams.Builder(responseBody).build())
            check(delivery.message.type == TYPE_DELIVERY) { "expected delivery, got ${delivery.message.type}" }

            val received = mutableListOf<ReceivedMessage>()
            val deliveredIds = mutableListOf<String>()

            for (attachment in delivery.message.attachments.orEmpty()) {
                val data = attachment.data
                if (data !is Attachment.Data.Base64) continue // this mediator only ever sends base64-variant attachments

                val innerPacked = String(Base64.decode(data.base64, Base64.URL_SAFE))
                val inner = didComm.unpack(UnpackParams.Builder(innerPacked).build())

                received += ReceivedMessage(
                    deliveryAttachmentId = attachment.id,
                    from = inner.message.from,
                    type = inner.message.type,
                    body = inner.message.body,
                )
                deliveredIds += attachment.id
            }

            if (deliveredIds.isNotEmpty()) {
                acknowledge(mediatorDid, myDid, deliveredIds)
            }

            received
        }

    private suspend fun acknowledge(mediatorDid: String, myDid: String, ids: List<String>) {
        val ackMessage = Message.builder(
            UUID.randomUUID().toString(),
            mapOf("message_id_list" to ids),
            TYPE_MESSAGES_RECEIVED
        )
            .from(myDid)
            .to(listOf(mediatorDid))
            // NOTE: unlike delivery-request, messages-received does NOT need
            // return_route - Pickup Protocol 4.0 explicitly dropped that
            // requirement for this message type (it's fire-and-forget; the
            // mediator responds 202 with nothing to unpack).
            .build()

        val packed = didComm.packEncrypted(
            PackEncryptedParams.builder(ackMessage, mediatorDid).from(myDid).forward(false).build()
        )
        val endpoint = packed.serviceMetadata?.serviceEndpoint
            ?: error("mediator DID has no resolvable service endpoint")

        transport.post(endpoint, packed.packedMessage)
    }
}