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

package eu.europa.ec.corelogic.resolver

import eu.europa.ec.networklogic.repository.DidRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.didcommx.didcomm.common.VerificationMaterial
import org.didcommx.didcomm.common.VerificationMaterialFormat
import org.didcommx.didcomm.common.VerificationMethodType
import org.didcommx.didcomm.diddoc.DIDCommService
import org.didcommx.didcomm.diddoc.DIDDoc
import org.didcommx.didcomm.diddoc.DIDDocResolver
import org.didcommx.didcomm.diddoc.VerificationMethod
import java.util.Optional

/**
 * Wraps network-logic's DidRepository as a didcomm-jvm DIDDocResolver.
 * IMPORTANT: DIDDocResolver.resolve() is a SYNCHRONOUS interface method
 * (confirmed against the library source - it is NOT a suspend function,
 * unlike almost everything else in this codebase). DidRepository, being
 * Ktor-based, is suspend. Bridging that gap means this resolver uses
 * runBlocking internally to call the suspend network function from a
 * synchronous context.
 *
 * The consequence that matters for every caller of this class: since
 * DIDComm's packEncrypted/unpack call this resolver internally (and are
 * THEMSELVES synchronous - also confirmed against the library source),
 * calling packEncrypted/unpack from application code means you are
 * performing blocking network I/O. NEVER call packEncrypted/unpack from
 * Dispatchers.Main - always wrap them in withContext(Dispatchers.IO).
 */
class LedgerDidDocResolver(
    private val didRepository: DidRepository,
    private val baseUrl: String,
) : DIDDocResolver {

    override fun resolve(did: String): Optional<DIDDoc> = runBlocking {
        val result = didRepository.resolve(baseUrl, did)
        val response = result.getOrNull() ?: return@runBlocking Optional.empty<DIDDoc>()

        // A deactivated DID resolves successfully but with a null document
        // (see ledger/routes.py's resolve_did) - callers should NOT encrypt
        // to a deactivated DID's keys, so this must be empty, not an error.
        val documentJson: JsonObject = response.didDocument
            ?: return@runBlocking Optional.empty<DIDDoc>()

        runCatching { mapToDidDoc(documentJson) }
            .fold(
                onSuccess = { Optional.of(it) },
                onFailure = { Optional.empty() } // malformed doc - treat as unresolvable, don't crash the caller
            )
    }

    private fun mapToDidDoc(document: JsonObject): DIDDoc {
        val didId = document["id"]!!.jsonPrimitive.content

        val verificationMethods = document["verificationMethod"]?.jsonArray.orEmpty().map { element ->
            val vm = element.jsonObject
            VerificationMethod(
                id = vm["id"]!!.jsonPrimitive.content,
                // This project only ever issues JsonWebKey2020 verification
                // methods (see ledger/document_utils.py's validation) - if
                // that ever changes server-side, this needs a real type
                // lookup instead of a hardcoded assumption.
                type = VerificationMethodType.JSON_WEB_KEY_2020,
                controller = vm["controller"]?.jsonPrimitive?.content ?: didId,
                verificationMaterial = VerificationMaterial(
                    format = VerificationMaterialFormat.JWK,
                    value = vm["publicKeyJwk"]!!.jsonObject.toString(),
                ),
            )
        }

        val keyAgreements = document["keyAgreement"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content }
        val authentications = document["authentication"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content }

        val didCommServices = document["service"]?.jsonArray.orEmpty()
            .map { it.jsonObject }
            .filter { it["type"]?.jsonPrimitive?.content == "DIDCommMessaging" }
            .map { service ->
                DIDCommService(
                    id = service["id"]!!.jsonPrimitive.content,
                    serviceEndpoint = service["serviceEndpoint"]!!.jsonPrimitive.content,
                    routingKeys = service["routingKeys"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content },
                    accept = service["accept"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                )
            }

        return DIDDoc(
            did = didId,
            keyAgreements = keyAgreements,
            authentications = authentications,
            verificationMethods = verificationMethods,
            didCommServices = didCommServices,
        )
    }
}