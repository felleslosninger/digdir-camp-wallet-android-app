package eu.europa.ec.networklogic.repository

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigInteger
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.util.Base64

internal const val INBOX_KEY_ALIAS = "digdir_inbox_signing_key"

internal fun ecPublicKeyJwkCoords(publicKey: ECPublicKey): Pair<String, String> {
    val encoder = Base64.getUrlEncoder().withoutPadding()
    return Pair(
        encoder.encodeToString(publicKey.w.affineX.toFixedBytes()),
        encoder.encodeToString(publicKey.w.affineY.toFixedBytes()),
    )
}

// RFC 7638 canonical form — key order and separators must match the server's _jwk_thumbprint().
internal fun jwkThumbprint(x: String, y: String): String {
    val canonical = """{"crv":"P-256","kty":"EC","x":"$x","y":"$y"}"""
    val hash = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
    return Base64.getUrlEncoder().withoutPadding().encodeToString(hash)
}

// Strips BigInteger sign byte and pads short arrays to the fixed coordinate width.
internal fun BigInteger.toFixedBytes(size: Int = 32): ByteArray {
    val bytes = toByteArray()
    return when {
        bytes.size == size + 1 && bytes[0] == 0.toByte() -> bytes.drop(1).toByteArray()
        bytes.size < size -> ByteArray(size - bytes.size) + bytes
        else -> bytes
    }
}

/**
 * Fetch a one-time challenge nonce for [thumbprint] and sign it with [privateKey].
 * Shared first step of every device-key authenticated call (fetch, read, refresh).
 */
internal suspend fun fetchSignedChallenge(
    httpClient: HttpClient,
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
