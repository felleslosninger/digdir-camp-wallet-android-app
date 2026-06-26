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
    suspend fun register(
        issuerBaseUrl: String,
        vct: String,
    ): Result<Unit>
}

class FcmRegistrationRepositoryImpl(
    private val httpClient: HttpClient
) : FcmRegistrationRepository {

    override suspend fun register(
        issuerBaseUrl: String,
        vct: String,
    ): Result<Unit> = runCatching {
        val token = suspendCancellableCoroutine { cont ->
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
        httpClient.post("$issuerBaseUrl/fcm/register") {
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonObject {
                    put("vct", JsonPrimitive(vct))
                    put("fcm_token", JsonPrimitive(token))
                }
            )
        }
    }
}
