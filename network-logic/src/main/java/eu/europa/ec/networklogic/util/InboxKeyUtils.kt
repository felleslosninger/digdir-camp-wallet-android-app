package eu.europa.ec.networklogic.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import eu.europa.ec.businesslogic.controller.storage.PrefKeys
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.Base64

internal const val INBOX_KEY_ALIAS_A = "digdir_inbox_signing_key_A"
internal const val INBOX_KEY_ALIAS_B = "digdir_inbox_signing_key_B"

internal fun ecPublicKeyJwkCoords(publicKey: ECPublicKey): Pair<String, String> {
    val encoder = Base64.getUrlEncoder().withoutPadding()
    return Pair(
        encoder.encodeToString(publicKey.w.affineX.toFixedBytes()),
        encoder.encodeToString(publicKey.w.affineY.toFixedBytes()),
    )
}

internal fun ecPublicKeyToJwk(publicKey: ECPublicKey): JsonObject {
    val (x, y) = ecPublicKeyJwkCoords(publicKey)
    return buildJsonObject {
        put("kty", JsonPrimitive("EC"))
        put("crv", JsonPrimitive("P-256"))
        put("x", JsonPrimitive(x))
        put("y", JsonPrimitive(y))
    }
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

internal fun createNewKeyPair(keyStore: KeyStore, alias: String): KeyPair {
    val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
    generator.initialize(
        KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .build()
    )
    return generator.generateKeyPair()
}

internal suspend fun getOrCreateInboxSigningKeyPair(prefKeys: PrefKeys, keyStore: KeyStore): KeyPair {
    val alias = prefKeys.getCurrentInboxKeySlot().ifBlank { INBOX_KEY_ALIAS_A }

    if (keyStore.containsAlias(alias)) {
        val entry = keyStore.getEntry(alias, null) as KeyStore.PrivateKeyEntry
        return KeyPair(entry.certificate.publicKey, entry.privateKey)
    }

    val keyPair = createNewKeyPair(keyStore, alias)
    prefKeys.setCurrentInboxKeySlot(alias)
    return keyPair
}

internal suspend fun fetchNonce(httpClient: HttpClient, issuerBaseUrl: String, thumbprint: String): String {
    val challengeText = httpClient
        .get("$issuerBaseUrl/inbox/fetch/challenge?thumbprint=$thumbprint")
        .bodyAsText()
    return Json.decodeFromString<JsonObject>(challengeText)["nonce"]
        ?.jsonPrimitive?.content ?: error("No nonce in challenge response")
}

// SHA256withECDSA produces DER-encoded output verified by the server
internal fun signPayload(payload: String, privateKey: PrivateKey): String {
    val signatureBytes = Signature.getInstance("SHA256withECDSA").apply {
        initSign(privateKey)
        update(payload.toByteArray(Charsets.UTF_8))
    }.sign()
    return Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes)
}

