package eu.europa.ec.networklogic.repository

import com.google.firebase.messaging.FirebaseMessaging
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface FcmRegistrationRepository {
    suspend fun subscribe(
        issuerBaseUrl: String,
        sessionToken: String,
    ): Result<Unit>

    suspend fun refresh(
        issuerBaseUrl: String,
        pidHash: String,
    ): Result<Unit>
}

class FcmRegistrationRepositoryImpl(
    private val httpClient: HttpClient
) : FcmRegistrationRepository {

    private suspend fun fcmToken(): String = suspendCancellableCoroutine { cont ->
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    override suspend fun subscribe(
        issuerBaseUrl: String,
        sessionToken: String,
    ): Result<Unit> = runCatching {
        val token = fcmToken()
        httpClient.post("$issuerBaseUrl/inbox/subscribe/register") {
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonObject {
                    put("session_token", JsonPrimitive(sessionToken))
                    put("fcm_token", JsonPrimitive(token))
                }
            )
        }
    }

    override suspend fun refresh(
        issuerBaseUrl: String,
        pidHash: String,
    ): Result<Unit> = runCatching {
        val token = fcmToken()
        httpClient.post("$issuerBaseUrl/inbox/subscribe/refresh") {
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonObject {
                    put("pid_hash", JsonPrimitive(pidHash))
                    put("fcm_token", JsonPrimitive(token))
                }
            )
        }
    }
}
