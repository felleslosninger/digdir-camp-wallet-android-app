package eu.europa.ec.networklogic.repository

import io.ktor.client.HttpClient
import io.ktor.client.request.get
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
import java.security.PrivateKey
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.util.Base64

data class InboxMessage(
    val id: String,
    val senderCn: String,
    val subject: String,
    val body: String,
    val sentAt: String,
    val status: String,
    val readAt: String? = null,
)

interface InboxRepository {
    suspend fun fetchMessages(issuerBaseUrl: String): Result<List<InboxMessage>>
    suspend fun markMessageRead(issuerBaseUrl: String, messageId: String): Result<Unit>
}

class InboxRepositoryImpl(
    private val httpClient: HttpClient,
) : InboxRepository {

    /** Fetch a one-time challenge nonce and sign it — shared first two steps of every device-key call. */
    private suspend fun signedChallenge(
        issuerBaseUrl: String,
        thumbprint: String,
        privateKey: PrivateKey,
    ): Pair<String, String> {
        val challengeText = httpClient
            .get("$issuerBaseUrl/inbox/fetch/challenge?thumbprint=$thumbprint")
            .bodyAsText()
        val nonce = Json.decodeFromString<JsonObject>(challengeText)["nonce"]
            ?.jsonPrimitive?.content ?: error("No nonce in challenge response")

        // SHA256withECDSA produces DER-encoded output verified by the server
        val signatureBytes = Signature.getInstance("SHA256withECDSA").apply {
            initSign(privateKey)
            update(nonce.toByteArray(Charsets.UTF_8))
        }.sign()
        val signatureB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes)
        return nonce to signatureB64
    }

    private fun inboxSigningEntry(): KeyStore.PrivateKeyEntry {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return keyStore.getEntry(INBOX_KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
            ?: error("No inbox signing key — subscribe first")
    }

    override suspend fun fetchMessages(issuerBaseUrl: String): Result<List<InboxMessage>> = runCatching {
        val entry = inboxSigningEntry()
        val (x, y) = ecPublicKeyJwkCoords(entry.certificate.publicKey as ECPublicKey)
        val thumbprint = jwkThumbprint(x, y)

        val (nonce, signatureB64) = signedChallenge(issuerBaseUrl, thumbprint, entry.privateKey)

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

        val (nonce, signatureB64) = signedChallenge(issuerBaseUrl, thumbprint, entry.privateKey)

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
