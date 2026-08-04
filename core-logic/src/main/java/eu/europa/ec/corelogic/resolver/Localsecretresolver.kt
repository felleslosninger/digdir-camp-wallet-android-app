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


import eu.europa.ec.businesslogic.didkeys.DidKeyStore
import org.didcommx.didcomm.secret.Secret
import org.didcommx.didcomm.secret.SecretResolver
import org.didcommx.didcomm.secret.jwkToSecret
import java.util.Optional

/**
 * Adapts business-logic's key storage to didcomm-jvm's SecretResolver
 * contract. Unlike DIDDocResolver, this one has no network dependency, so
 * no runBlocking bridge is needed here - findKey/findKeys are genuinely
 * synchronous local lookups.
 *
 * jwkToSecret() is didcomm-jvm's own helper (confirmed against the library
 * source, in org.didcommx.didcomm.secret.SecretUtils) - it reads the JWK's
 * own "kid" field, so the JWK map returned by DidKeyStore must already have
 * "kid" set correctly (see DidKeyStore.generateIdentityKeys, which does this).
 */
class LocalSecretResolver(private val keyStore: DidKeyStore) : SecretResolver {

    override fun findKey(kid: String): Optional<Secret> {
        val jwk = keyStore.getPrivateJwk(kid) ?: return Optional.empty()
        return Optional.of(jwkToSecret(jwk))
    }

    override fun findKeys(kids: List<String>): Set<String> {
        val ourKids = keyStore.allKids().toSet()
        return kids.filter { it in ourKids }.toSet()
    }
}