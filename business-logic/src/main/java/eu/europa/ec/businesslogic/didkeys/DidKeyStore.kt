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

package eu.europa.ec.businesslogic.didkeys


import org.didcommx.didcomm.secret.KeyPair
import org.didcommx.didcomm.secret.generateEd25519Keys
import org.didcommx.didcomm.secret.generateX25519Keys

/**
 * PLACEHOLDER - in-memory only, lost on process death. This is deliberately
 * minimal to unblock a working send/receive round trip.
 */
interface DidKeyStore {
    /** Generates and stores a fresh Ed25519 (signing) + X25519 (key
     * agreement) keypair for [did], keyed by kid = "$did#key-1" / "#key-2".
     * Returns the two PUBLIC jwks, for building the DID Document to register. */
    fun generateIdentityKeys(did: String): IdentityKeys

    /** Raw private key JWK (includes "d") for a given kid, or null if we
     * don't hold that key. This is exactly what SecretResolverImpl needs. */
    fun getPrivateJwk(kid: String): Map<String, Any>?

    /** All kids we currently hold private keys for. */
    fun allKids(): List<String>
}

data class IdentityKeys(
    val authKid: String,
    val authPublicJwk: Map<String, Any>,
    val agreementKid: String,
    val agreementPublicJwk: Map<String, Any>,
)

class InMemoryDidKeyStore : DidKeyStore {

    private val privateKeys = mutableMapOf<String, Map<String, Any>>()

    override fun generateIdentityKeys(did: String): IdentityKeys {
        val authKid = "$did#key-1"
        val agreementKid = "$did#key-2"

        val authKeys: KeyPair = generateEd25519Keys()
        val agreementKeys: KeyPair = generateX25519Keys()

        val authPrivate = authKeys.private + mapOf("kid" to authKid)
        val authPublic = authKeys.public + mapOf("kid" to authKid)
        val agreementPrivate = agreementKeys.private + mapOf("kid" to agreementKid)
        val agreementPublic = agreementKeys.public + mapOf("kid" to agreementKid)

        privateKeys[authKid] = authPrivate
        privateKeys[agreementKid] = agreementPrivate

        return IdentityKeys(authKid, authPublic, agreementKid, agreementPublic)
    }

    override fun getPrivateJwk(kid: String): Map<String, Any>? = privateKeys[kid]

    override fun allKids(): List<String> = privateKeys.keys.toList()
}