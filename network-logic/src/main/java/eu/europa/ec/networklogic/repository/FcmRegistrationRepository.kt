package eu.europa.ec.networklogic.repository

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.google.firebase.messaging.FirebaseMessaging
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonObject
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface FcmRegistrationRepository {
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

    override suspend fun subscribe(issuerBaseUrl: String, sessionToken: String): Result<Unit> = runCatching {
        val token = fcmToken()
        val keyPair = getOrCreateSigningKeyPair()
        val (x, y) = ecPublicKeyJwkCoords(keyPair.public as ECPublicKey)
        val thumbprint = jwkThumbprint(x, y)

        httpClient.post("$issuerBaseUrl/inbox/subscribe/register") {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("session_token", JsonPrimitive(sessionToken))
                put("fcm_token", JsonPrimitive(token))
                put("thumbprint", JsonPrimitive(thumbprint))
                putJsonObject("public_key_jwk") {
                    put("kty", JsonPrimitive("EC"))
                    put("crv", JsonPrimitive("P-256"))
                    put("x", JsonPrimitive(x))
                    put("y", JsonPrimitive(y))
                }
            })
        }
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
