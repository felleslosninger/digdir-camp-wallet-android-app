package eu.europa.ec.networklogic.repository

import eu.europa.ec.businesslogic.controller.storage.PrefKeys
import eu.europa.ec.networklogic.util.INBOX_KEY_ALIAS_A
import eu.europa.ec.networklogic.util.INBOX_KEY_ALIAS_B
import eu.europa.ec.networklogic.util.createNewKeyPair
import eu.europa.ec.networklogic.util.ecPublicKeyJwkCoords
import eu.europa.ec.networklogic.util.ecPublicKeyToJwk
import eu.europa.ec.networklogic.util.fetchNonce
import eu.europa.ec.networklogic.util.jwkThumbprint
import eu.europa.ec.networklogic.util.signPayload
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.KeyStore
import java.security.interfaces.ECPublicKey

// Kotlin data classes can't be `open`, so this is a plain class with hand-written
// copy()/equals()/hashCode() — InboxMessageUi (dashboard-feature) extends it directly.
open class InboxMessage(
    val id: String,
    val senderCn: String,
    val subject: String,
    val body: String,
    val sentAt: String,
    val status: String,
    val readAt: String? = null,
) {
    fun copy(
        id: String = this.id,
        senderCn: String = this.senderCn,
        subject: String = this.subject,
        body: String = this.body,
        sentAt: String = this.sentAt,
        status: String = this.status,
        readAt: String? = this.readAt,
    ): InboxMessage = InboxMessage(id, senderCn, subject, body, sentAt, status, readAt)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is InboxMessage) return false
        return id == other.id && senderCn == other.senderCn && subject == other.subject &&
                body == other.body && sentAt == other.sentAt && status == other.status &&
                readAt == other.readAt
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + senderCn.hashCode()
        result = 31 * result + subject.hashCode()
        result = 31 * result + body.hashCode()
        result = 31 * result + sentAt.hashCode()
        result = 31 * result + status.hashCode()
        result = 31 * result + (readAt?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String =
        "InboxMessage(id=$id, senderCn=$senderCn, subject=$subject, body=$body, " +
                "sentAt=$sentAt, status=$status, readAt=$readAt)"
}

interface InboxRepository {
    suspend fun fetchMessages(issuerBaseUrl: String): Result<List<InboxMessage>>
    suspend fun markMessageRead(issuerBaseUrl: String, messageId: String): Result<Unit>
    suspend fun recoverKeyState(issuerBaseUrl: String)
}

class InboxRepositoryImpl(
    private val httpClient: HttpClient,
    private val prefKeys: PrefKeys,
) : InboxRepository {

    private suspend fun getInboxKeySlot(): Pair<String, String> {
        val currentSlot = prefKeys.getCurrentInboxKeySlot()
        val nextSlot = if (currentSlot == INBOX_KEY_ALIAS_A) INBOX_KEY_ALIAS_B else INBOX_KEY_ALIAS_A

        return Pair(currentSlot, nextSlot)
    }

    override suspend fun fetchMessages(issuerBaseUrl: String): Result<List<InboxMessage>> = runCatching {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

        val (currentSlot, nextSlot) = getInboxKeySlot()
        val currentKeyPair = keyStore.getEntry(currentSlot, null) as? KeyStore.PrivateKeyEntry
            ?: error("No inbox signing key — subscribe first")
        val (x, y) = ecPublicKeyJwkCoords(currentKeyPair.certificate.publicKey as ECPublicKey)
        val currentThumbprint = jwkThumbprint(x, y)

        // generate the next key
        keyStore.deleteEntry(nextSlot) // clear any leftover from an aborted rotation
        val nextKeyPair = createNewKeyPair(keyStore, nextSlot)
        val (nx, ny) = ecPublicKeyJwkCoords(nextKeyPair.public as ECPublicKey)
        val nextThumbprint = jwkThumbprint(nx, ny)

        prefKeys.setInboxRotationInFlight(true)

        val nonce = fetchNonce(httpClient, issuerBaseUrl, currentThumbprint)
        val signatureB64 = signPayload("$nonce.$nextThumbprint", currentKeyPair.privateKey)

        val response = httpClient.post("$issuerBaseUrl/inbox/fetch") {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("thumbprint", JsonPrimitive(currentThumbprint))
                put("nonce", JsonPrimitive(nonce))
                put("signature", JsonPrimitive(signatureB64))
                put("next_public_key_jwk", ecPublicKeyToJwk(nextKeyPair.public as ECPublicKey))
            })
        }

        if (response.status != HttpStatusCode.OK) {
            keyStore.deleteEntry(nextSlot) // rotation didn't happen, discard the next key
            prefKeys.setInboxRotationInFlight(false)
            error("Failed to fetch messages: ${response.status}")
        }

        keyStore.deleteEntry(currentSlot)
        prefKeys.setCurrentInboxKeySlot(nextSlot)
        prefKeys.setInboxRotationInFlight(false)

        val fetchJson = Json.decodeFromString<JsonObject>(response.bodyAsText())
        fetchJson["messages"]?.jsonArray?.map { el ->
            val m = el.jsonObject
            InboxMessage(
                id = m["id"]?.jsonPrimitive?.content.orEmpty(),
                senderCn = m["sender_org_number"]?.jsonPrimitive?.content.orEmpty(),
                subject = m["tittel"]?.jsonPrimitive?.content.orEmpty(),
                body = m["innhold"]?.jsonPrimitive?.content.orEmpty(),
                sentAt = m["sent_at"]?.jsonPrimitive?.content.orEmpty(),
                status = m["status"]?.jsonPrimitive?.content.orEmpty(),
                readAt = m["read_at"]?.jsonPrimitive?.contentOrNull,
            )
        } ?: emptyList()
    }

    /**
     * Call on app launch. Resolves a rotation that may have committed server-side without the
     * client finding out (crash/kill between the POST /inbox/fetch response and the local
     * alias flip). No-op unless a rotation was left in flight.
     */
    override suspend fun recoverKeyState(issuerBaseUrl: String) {
        if (!prefKeys.getInboxRotationInFlight()) return

        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val currentSlot = prefKeys.getCurrentInboxKeySlot().ifBlank { return }
        val nextSlot = getInboxKeySlot().second

        val currentEntry = keyStore.getEntry(currentSlot, null) as? KeyStore.PrivateKeyEntry
        if (currentEntry == null) {
            // currentSlot's key is already gone locally — the only way that happens is
            // fetchMessages() got a 200, deleted it, then crashed before flipping the pointer.
            prefKeys.setCurrentInboxKeySlot(nextSlot)
            prefKeys.setInboxRotationInFlight(false)
            return
        }

        val (x, y) = ecPublicKeyJwkCoords(currentEntry.certificate.publicKey as ECPublicKey)
        val currentThumbprint = jwkThumbprint(x, y)

        val probeStatus = try {
            httpClient.get("$issuerBaseUrl/inbox/fetch/challenge") {
                parameter("thumbprint", currentThumbprint)
            }.status
        } catch (e: Exception) {
            return // no connectivity — retry on next launch, still marked in flight
        }

        if (probeStatus == HttpStatusCode.NotFound) {
            // server already committed the rotation — promote the already-generated successor
            keyStore.deleteEntry(currentSlot)
            prefKeys.setCurrentInboxKeySlot(nextSlot)
        } else {
            // rotation never reached the server — drop the unconfirmed successor
            keyStore.deleteEntry(nextSlot)
        }
        prefKeys.setInboxRotationInFlight(false)
    }

    override suspend fun markMessageRead(issuerBaseUrl: String, messageId: String): Result<Unit> = runCatching {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val currentSlot = prefKeys.getCurrentInboxKeySlot()
            .ifBlank { error("No inbox signing key — subscribe first") }
        val entry = keyStore.getEntry(currentSlot, null) as? KeyStore.PrivateKeyEntry
            ?: error("No inbox signing key — subscribe first")
        val (x, y) = ecPublicKeyJwkCoords(entry.certificate.publicKey as ECPublicKey)
        val thumbprint = jwkThumbprint(x, y)

        val nonce = fetchNonce(httpClient, issuerBaseUrl, thumbprint)
        val signatureB64 = signPayload(nonce, entry.privateKey)

        httpClient.post("$issuerBaseUrl/inbox/read") {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("thumbprint", JsonPrimitive(thumbprint))
                put("nonce", JsonPrimitive(nonce))
                put("signature", JsonPrimitive(signatureB64))
                put("message_id", JsonPrimitive(messageId))
            })
        }
        Unit
    }
}
