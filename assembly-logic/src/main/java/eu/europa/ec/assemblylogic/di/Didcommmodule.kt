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

package eu.europa.ec.assemblylogic.di


import eu.europa.ec.businesslogic.didkeys.DidKeyStore
import eu.europa.ec.businesslogic.didkeys.InMemoryDidKeyStore
import eu.europa.ec.corelogic.mediator.MediatorClient
import eu.europa.ec.corelogic.messaging.MessagingRepository
import eu.europa.ec.corelogic.resolver.LedgerDidDocResolver
import eu.europa.ec.corelogic.resolver.LocalSecretResolver
import eu.europa.ec.networklogic.didcomm.DidCommTransport
import eu.europa.ec.networklogic.didcomm.KtorDidCommTransport
import eu.europa.ec.networklogic.repository.DidRepository
import io.ktor.client.HttpClient
import org.didcommx.didcomm.DIDComm
import org.didcommx.didcomm.diddoc.DIDDocResolver
import org.didcommx.didcomm.secret.SecretResolver
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
*
 */
val didCommModule = module {

    single<String>(named("didLedgerBaseUrl")) { "https://your-server.example.com" }

    single<DidKeyStore> { InMemoryDidKeyStore() }

    single<DidCommTransport> { KtorDidCommTransport(get<HttpClient>()) }

    single<DIDDocResolver> {
        LedgerDidDocResolver(
            didRepository = get<DidRepository>(),
            baseUrl = get(named("didLedgerBaseUrl")),
        )
    }

    single<SecretResolver> { LocalSecretResolver(get()) }

    single { DIDComm(get<DIDDocResolver>(), get<SecretResolver>()) }

    single { MediatorClient(didComm = get(), transport = get()) }

    single { MessagingRepository(didComm = get(), transport = get()) }
}