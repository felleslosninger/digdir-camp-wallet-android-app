package eu.europa.ec.networklogic.repository

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.google.firebase.messaging.FirebaseMessaging
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.putJsonObject
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface FcmRegistrationRepository {
    suspend fun startSubscribeFlow(inboxBaseUrl: String): Result<String>
    suspend fun subscribe(issuerBaseUrl: String, sessionToken: String): Result<Unit>
    suspend fun refresh(issuerBaseUrl: String, pidHash: String): Result<Unit>
}

class FcmRegistrationRepositoryImpl(
    private val httpClient: HttpClient,
) : FcmRegistrationRepository {

    private suspend fun fcmToken(): String = suspendCancellableCoroutine { cont ->
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    private fun getOrCreateSigningKeyPair(): KeyPair {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (keyStore.containsAlias(INBOX_KEY_ALIAS)) {
            val entry = keyStore.getEntry(INBOX_KEY_ALIAS, null) as KeyStore.PrivateKeyEntry
            return KeyPair(entry.certificate.publicKey, entry.privateKey)
        }
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
        generator.initialize(
            KeyGenParameterSpec.Builder(INBOX_KEY_ALIAS, KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build()
        )
        return generator.generateKeyPair()
    }

    override suspend fun startSubscribeFlow(inboxBaseUrl: String): Result<String> = runCatching {
        val keyPair = getOrCreateSigningKeyPair()
        val (x, y) = ecPublicKeyJwkCoords(keyPair.public as ECPublicKey)
        val thumbprint = jwkThumbprint(x, y)

        val responseText = httpClient.get("$inboxBaseUrl/inbox/subscribe/start") {
            parameter("key_thumbprint", thumbprint)
        }.bodyAsText()

        Json.decodeFromString<JsonObject>(responseText)["oid4vp_uri"]
            ?.jsonPrimitive?.content ?: error("Missing oid4vp_uri in response")
    }

    override suspend fun subscribe(issuerBaseUrl: String, sessionToken: String): Result<Unit> = runCatching {
        val token = fcmToken()
        val keyPair = getOrCreateSigningKeyPair()
        val (x, y) = ecPublicKeyJwkCoords(keyPair.public as ECPublicKey)
        val thumbprint = jwkThumbprint(x, y)
        val popJwt = buildPopJwt(sessionToken, keyPair.private)

        httpClient.post("$issuerBaseUrl/inbox/subscribe/register") {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("session_token", JsonPrimitive(sessionToken))
                put("fcm_token", JsonPrimitive(token))
                put("pop_jwt", JsonPrimitive(popJwt))
                putJsonObject("public_key_jwk") {
                    put("kty", JsonPrimitive("EC"))
                    put("crv", JsonPrimitive("P-256"))
                    put("x", JsonPrimitive(x))
                    put("y", JsonPrimitive(y))
                }
            })
        }
    }

    private fun buildPopJwt(sessionToken: String, privateKey: PrivateKey): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("""{"alg":"ES256","typ":"JWT"}""".toByteArray())
        val nowSecs = System.currentTimeMillis() / 1000
        val payload = encoder.encodeToString(
            """{"sub":"$sessionToken","jti":"${UUID.randomUUID()}","iat":$nowSecs,"exp":${nowSecs + 300}}"""
                .toByteArray()
        )
        val signingInput = "$header.$payload".toByteArray(Charsets.UTF_8)
        val derSig = Signature.getInstance("SHA256withECDSA").run {
            initSign(privateKey)
            update(signingInput)
            sign()
        }
        val p1363Sig = derToP1363(derSig)
        val sig = encoder.encodeToString(p1363Sig)
        return "$header.$payload.$sig"
    }

    private fun derToP1363(der: ByteArray): ByteArray {
        // DER SEQUENCE { INTEGER r, INTEGER s } → 64-byte r‖s (IEEE P1363)
        var offset = 2 // skip SEQUENCE tag + length
        val rLen = der[offset + 1].toInt() and 0xFF
        val rBytes = der.slice((offset + 2)..(offset + 1 + rLen)).toByteArray()
        offset += 2 + rLen
        val sLen = der[offset + 1].toInt() and 0xFF
        val sBytes = der.slice((offset + 2)..(offset + 1 + sLen)).toByteArray()

        fun pad32(b: ByteArray): ByteArray = when {
            b.size == 33 && b[0] == 0.toByte() -> b.drop(1).toByteArray()
            b.size < 32 -> ByteArray(32 - b.size) + b
            else -> b
        }
        return pad32(rBytes) + pad32(sBytes)
    }

    override suspend fun refresh(issuerBaseUrl: String, pidHash: String): Result<Unit> = runCatching {
        val token = fcmToken()
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

        if (keyStore.containsAlias(INBOX_KEY_ALIAS)) {
            val entry = keyStore.getEntry(INBOX_KEY_ALIAS, null) as KeyStore.PrivateKeyEntry
            val (x, y) = ecPublicKeyJwkCoords(entry.certificate.publicKey as ECPublicKey)
            val thumbprint = jwkThumbprint(x, y)
            httpClient.post("$issuerBaseUrl/inbox/subscribe/refresh") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("thumbprint", JsonPrimitive(thumbprint))
                    put("fcm_token", JsonPrimitive(token))
                })
            }
        } else {
            // No key yet (never subscribed on this device): fall back to pid_hash
            httpClient.post("$issuerBaseUrl/inbox/subscribe/refresh") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("pid_hash", JsonPrimitive(pidHash))
                    put("fcm_token", JsonPrimitive(token))
                })
            }
        }
    }
}
