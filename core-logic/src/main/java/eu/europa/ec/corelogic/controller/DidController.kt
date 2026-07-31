/*
 * Copyright (c) 2026 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work
 * except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific language
 * governing permissions and limitations under the Licence.
 */

package eu.europa.ec.corelogic.controller

import android.util.Base64
import eu.europa.ec.businesslogic.controller.storage.PrefKeys
import eu.europa.ec.businesslogic.extension.encodeToBase64String
import eu.europa.ec.corelogic.config.WalletCoreConfig
import eu.europa.ec.corelogic.util.DidCryptoUtil
import eu.europa.ec.eudi.wallet.EudiWallet
import eu.europa.ec.networklogic.repository.DidCreateRequest
import eu.europa.ec.networklogic.repository.DidDeactivateRequest
import eu.europa.ec.networklogic.repository.DidProof
import eu.europa.ec.networklogic.repository.DidRepository
import eu.europa.ec.networklogic.repository.DidResponse
import eu.europa.ec.networklogic.repository.DidUpdateRequest
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcCurve
import org.multipaz.crypto.Reason
import org.multipaz.securearea.CreateKeySettings
import org.multipaz.securearea.SecureArea
import org.multipaz.securearea.software.SoftwareSecureArea

interface DidController {
    suspend fun createDid(): Result<DidResponse>
    suspend fun resolveDid(did: String): Result<DidResponse>
    suspend fun updateDid(newDidDocument: JsonObject): Result<DidResponse>
    suspend fun deactivateDid(): Result<DidResponse>
}

class DidControllerImpl(
    private val didRepository: DidRepository,
    private val prefKeys: PrefKeys,
    private val walletCoreConfig: WalletCoreConfig,
    private val eudiWallet: EudiWallet
) : DidController {

    private companion object {
        const val DID_KEY_ALIAS = "did_auth_key"
    }

    private val baseUrl: String get() = walletCoreConfig.walletProviderHost

    private suspend fun getSecureArea(): SecureArea {
        // Using SoftwareSecureArea as it consistently supports Ed25519 across all Android versions
        return eudiWallet.secureAreaRepository.getImplementation(SoftwareSecureArea.IDENTIFIER)
            ?: throw IllegalStateException("SoftwareSecureArea not found")
    }

    override suspend fun createDid(): Result<DidResponse> = runCatching {
        val secureArea = getSecureArea()
        
        // 1. Generate Ed25519 key if it doesn't exist
        val existingKey = runCatching { secureArea.getKeyInfo(DID_KEY_ALIAS) }.getOrNull()
        if (existingKey == null) {
            // Need to provide a valid settings object. 
            // SoftwareSecureArea uses a generic CreateKeySettings if no specific settings are needed.
            secureArea.createKey(DID_KEY_ALIAS, object : CreateKeySettings(Algorithm.EDDSA) {})
        }
        val keyInfo = secureArea.getKeyInfo(DID_KEY_ALIAS)
        val jwk = keyInfo.publicKey.toJwk()

        // 2. Get a fresh DID from server
        val mintedDid = didRepository.mintId(baseUrl).getOrThrow()

        // 3. Build DID Document
        val didDocument = buildJsonObject {
            put("id", mintedDid)
            put("authentication", buildJsonObject {
                put("id", "$mintedDid#keys-1")
                put("type", "JsonWebKey2020")
                put("controller", mintedDid)
                put("publicKeyJwk", jwk)
            })
        }

        // 4. Canonicalize and Sign
        val messageToSign = DidCryptoUtil.canonicalize(didDocument).toByteArray()
        val signature = secureArea.sign(DID_KEY_ALIAS, messageToSign, Reason.Unspecified)
        val signatureBase64 = signature.toCoseEncoded().encodeToBase64String(Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

        // 5. Send Request
        val request = DidCreateRequest(
            didDocument = didDocument,
            proof = DidProof(signature = signatureBase64)
        )
        
        val response = didRepository.create(baseUrl, request).getOrThrow()
        
        // Store DID info in Prefs (implement these in PrefKeys if needed)
        // prefKeys.setString("active_did", response.did)
        // prefKeys.setString("active_did_version", response.didDocumentMetadata.versionId ?: "")
        
        response
    }

    override suspend fun resolveDid(did: String): Result<DidResponse> {
        return didRepository.resolve(baseUrl, did)
    }

    override suspend fun updateDid(newDidDocument: JsonObject): Result<DidResponse> = runCatching {
        val secureArea = getSecureArea()
        val did = newDidDocument["id"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("DID Document must contain an 'id'")

        // 1. Resolve for å hente gjeldende versionId
        val currentResponse = resolveDid(did).getOrThrow()
        val versionId = currentResponse.didDocumentMetadata.versionId ?: ""

        // 2. Bygg meldingen som skal signeres: canonicalize(newDoc) + "." + versionId
        val canonicalDoc = DidCryptoUtil.canonicalize(newDidDocument)
        val messageToSign = "$canonicalDoc.$versionId".toByteArray()

        // 3. Signer
        val signature = secureArea.sign(DID_KEY_ALIAS, messageToSign, org.multipaz.crypto.Reason.Unspecified)
        val signatureBase64 = signature.toCoseEncoded().encodeToBase64String(Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

        // 4. Send Update
        val request = DidUpdateRequest(
            didDocument = newDidDocument,
            proof = DidProof(signature = signatureBase64)
        )
        
        didRepository.update(baseUrl, did, request).getOrThrow()
    }

    override suspend fun deactivateDid(): Result<DidResponse> = runCatching {
        val secureArea = getSecureArea()
        
        // Vi antar her at vi deaktiverer den DID-en som er lagret lokalt
        // For enkelhet i denne camp-løsningen, henter vi den via en resolve først
        // (I en ekte app ville du lagret DID-en din i Prefs)
        val keyInfo = secureArea.getKeyInfo(DID_KEY_ALIAS)
        val jwk = keyInfo.publicKey.toJwk()
        
        // Finn DID-en vår (vi må vite hvilken vi skal deaktivere)
        // Her bør du egentlig ha lagret 'did' fra create-steget
        val did = "did:yourmethod:..." // TODO: Hent fra lagring

        val currentResponse = resolveDid(did).getOrThrow()
        val versionId = currentResponse.didDocumentMetadata.versionId ?: ""

        // 1. Bygg meldingen: "deactivate:<did>:<versionId>"
        val messageToSign = "deactivate:$did:$versionId".toByteArray()

        // 2. Signer
        val signature = secureArea.sign(DID_KEY_ALIAS, messageToSign, org.multipaz.crypto.Reason.Unspecified)
        val signatureBase64 = signature.toCoseEncoded().encodeToBase64String(Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

        // 3. Send Deactivate
        val request = DidDeactivateRequest(
            proof = DidProof(signature = signatureBase64)
        )
        
        didRepository.deactivate(baseUrl, did, request).getOrThrow()
    }
}
