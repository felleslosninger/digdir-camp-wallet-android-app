package eu.europa.ec.networklogic.repository

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
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
}

class InboxRepositoryImpl(
    private val httpClient: HttpClient,
) : InboxRepository {

    private fun inboxSigningEntry(): KeyStore.PrivateKeyEntry {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return keyStore.getEntry(INBOX_KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
            ?: error("No inbox signing key — subscribe first")
    }

    override suspend fun fetchMessages(issuerBaseUrl: String): Result<List<InboxMessage>> = runCatching {
        val entry = inboxSigningEntry()
        val (x, y) = ecPublicKeyJwkCoords(entry.certificate.publicKey as ECPublicKey)
        val thumbprint = jwkThumbprint(x, y)

        val (nonce, signatureB64) = fetchSignedChallenge(httpClient, issuerBaseUrl, thumbprint, entry.privateKey)

        val fetchText = httpClient.post("$issuerBaseUrl/inbox/fetch") {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("thumbprint", JsonPrimitive(thumbprint))
                put("nonce", JsonPrimitive(nonce))
                put("signature", JsonPrimitive(signatureB64))
            })
        }.bodyAsText()

        val fetchJson = Json.decodeFromString<JsonObject>(fetchText)
        fetchJson["messages"]?.jsonArray?.map { el ->
            val m = el.jsonObject
            InboxMessage(
                id = m["id"]?.jsonPrimitive?.content.orEmpty(),
                senderCn = m["sender_cn"]?.jsonPrimitive?.content.orEmpty(),
                subject = m["subject"]?.jsonPrimitive?.content.orEmpty(),
                body = m["body"]?.jsonPrimitive?.content.orEmpty(),
                sentAt = m["sent_at"]?.jsonPrimitive?.content.orEmpty(),
                status = m["status"]?.jsonPrimitive?.content.orEmpty(),
                readAt = m["read_at"]?.jsonPrimitive?.contentOrNull,
            )
        } ?: emptyList()
    }

    override suspend fun markMessageRead(issuerBaseUrl: String, messageId: String): Result<Unit> = runCatching {
        val entry = inboxSigningEntry()
        val (x, y) = ecPublicKeyJwkCoords(entry.certificate.publicKey as ECPublicKey)
        val thumbprint = jwkThumbprint(x, y)

        val (nonce, signatureB64) = fetchSignedChallenge(httpClient, issuerBaseUrl, thumbprint, entry.privateKey)

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
