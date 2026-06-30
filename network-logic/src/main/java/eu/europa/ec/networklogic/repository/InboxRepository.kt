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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.KeyStore
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
)

interface InboxRepository {
    suspend fun fetchMessages(issuerBaseUrl: String): Result<List<InboxMessage>>
}

class InboxRepositoryImpl(
    private val httpClient: HttpClient,
) : InboxRepository {

    override suspend fun fetchMessages(issuerBaseUrl: String): Result<List<InboxMessage>> = runCatching {
        // Fetching messages requires a proof of possession of the inbox signing key
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val entry = keyStore.getEntry(INBOX_KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
            ?: error("No inbox signing key — subscribe first")

        val (x, y) = ecPublicKeyJwkCoords(entry.certificate.publicKey as ECPublicKey)
        val thumbprint = jwkThumbprint(x, y)

        // Step 1: fetch challenge nonce
        val challengeText = httpClient
            .get("$issuerBaseUrl/inbox/fetch/challenge?thumbprint=$thumbprint")
            .bodyAsText()
        val nonce = Json.decodeFromString<JsonObject>(challengeText)["nonce"]
            ?.jsonPrimitive?.content ?: error("No nonce in challenge response")

        // Step 2: sign nonce (SHA256withECDSA produces DER-encoded output verified by the server)
        val signatureBytes = Signature.getInstance("SHA256withECDSA").apply {
            initSign(entry.privateKey)
            update(nonce.toByteArray(Charsets.UTF_8))
        }.sign()
        val signatureB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes)

        // Step 3: POST signed proof and receive messages
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
            )
        } ?: emptyList()
    }
}
