/*
 * Copyright (c) 2023 European Commission
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

package eu.europa.ec.corelogic.config

import android.content.Context
import eu.europa.ec.corelogic.BuildConfig
import eu.europa.ec.corelogic.model.DocumentIdentifier
import eu.europa.ec.eudi.wallet.EudiWalletConfig
import eu.europa.ec.eudi.wallet.document.CreateDocumentSettings.CredentialPolicy
import eu.europa.ec.eudi.wallet.issue.openid4vci.OpenId4VciManager
import eu.europa.ec.eudi.wallet.issue.openid4vci.dpop.DPopConfig
import eu.europa.ec.eudi.wallet.transfer.openId4vp.ClientIdScheme
import eu.europa.ec.eudi.wallet.transfer.openId4vp.Format
import eu.europa.ec.resourceslogic.R
import java.time.Duration
import kotlin.time.Duration.Companion.seconds

internal class WalletCoreConfigImpl(
    private val context: Context
) : WalletCoreConfig {

    private companion object {
        const val VCI_ISSUER_URL = "https://utsteder.test.eidas2sandkasse.net/pid"
        const val VCI_LOCAL_ISSUER_URL = "https://localhost:5443"
        const val VCI_CLIENT_ID = "wallet-dev"
        const val AUTHENTICATION_REQUIRED = false
    }

    private var _config: EudiWalletConfig? = null

    override val config: EudiWalletConfig
        get() {
            if (_config == null) {
                _config = EudiWalletConfig {
                    configureDocumentKeyCreation(
                        userAuthenticationRequired = false,
                        userAuthenticationTimeout = 30.seconds,
                        useStrongBoxForKeys = true
                    )
                    configureOpenId4Vp {
                        withClientIdSchemes(
                            listOf(
                                ClientIdScheme.X509SanDns,
                                ClientIdScheme.X509Hash
                            )
                        )
                        withSchemes(
                            listOf(
                                BuildConfig.OPENID4VP_SCHEME,
                                BuildConfig.EUDI_OPENID4VP_SCHEME,
                                BuildConfig.MDOC_OPENID4VP_SCHEME,
                                BuildConfig.HAIP_OPENID4VP_SCHEME
                            )
                        )
                        withFormats(
                            Format.MsoMdoc.ES256, Format.SdJwtVc.ES256
                        )
                    }

                    configureDCAPI {
                        withEnabled(true)
                    }

                    configureReaderTrustStore(
                        context,
                        R.raw.intermediate_brukersted_ca,
                        R.raw.access2
                    )
                }
            }
            return _config!!
        }

    override val issuersConfig: List<VciConfig>
        get() = listOf(
            VciConfig(
                config = OpenId4VciManager.Config.Builder()
                    .withIssuerUrl(issuerUrl = VCI_LOCAL_ISSUER_URL)
                    .withClientAuthenticationType(OpenId4VciManager.ClientAuthenticationType.None(VCI_CLIENT_ID))
                    .withAuthFlowRedirectionURI(BuildConfig.ISSUE_AUTHORIZATION_DEEPLINK)
                    .withParUsage(OpenId4VciManager.Config.ParUsage.IF_SUPPORTED)
                    .withDPopConfig(DPopConfig.Default)
                    .build(),
                order = 0
            ),
            VciConfig(
                config = OpenId4VciManager.Config.Builder()
                    .withIssuerUrl(issuerUrl = VCI_ISSUER_URL)
                    .withClientAuthenticationType(OpenId4VciManager.ClientAuthenticationType.None(VCI_CLIENT_ID))
                    .withAuthFlowRedirectionURI(BuildConfig.ISSUE_AUTHORIZATION_DEEPLINK)
                    .withParUsage(OpenId4VciManager.Config.ParUsage.IF_SUPPORTED)
                    .withDPopConfig(DPopConfig.Default)
                    .build(),
                order = 1
            ),
            VciConfig(
                config = OpenId4VciManager.Config.Builder()
                    .withIssuerUrl(issuerUrl = VCI_ISSUER_URL)
                    .withClientAuthenticationType(OpenId4VciManager.ClientAuthenticationType.None(VCI_CLIENT_ID))
                    .withAuthFlowRedirectionURI(BuildConfig.ISSUE_AUTHORIZATION_DEEPLINK)
                    .withParUsage(OpenId4VciManager.Config.ParUsage.IF_SUPPORTED)
                    .withDPopConfig(DPopConfig.Default)
                    .build(),
                order = 2
            )
        )

    override val documentIssuanceConfig: DocumentIssuanceConfig
        get() = DocumentIssuanceConfig(
            defaultRule = DocumentIssuanceRule(
                policy = CredentialPolicy.RotateUse,
                numberOfCredentials = 1
            ),
            documentSpecificRules = mapOf(
                DocumentIdentifier.MdocPid to DocumentIssuanceRule(
                    policy = CredentialPolicy.OneTimeUse,
                    numberOfCredentials = 10
                ),
                DocumentIdentifier.SdJwtPid to DocumentIssuanceRule(
                    policy = CredentialPolicy.OneTimeUse,
                    numberOfCredentials = 10
                ),
            ),
            reissuanceRule = ReIssuanceRule(
                minNumberOfCredentials = 2,
                minExpirationHours = 24,
                backgroundInterval = Duration.ofMinutes(15)
            )
        )

    override val walletProviderHost: String
        get() = "https://dev.wallet-provider.eudiw.dev"
}