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

package eu.europa.ec.corelogic.extension

import eu.europa.ec.businesslogic.extension.getLocalizedValue
import eu.europa.ec.eudi.wallet.document.Document
import eu.europa.ec.eudi.wallet.document.IssuedDocument
import eu.europa.ec.eudi.wallet.document.format.SdJwtVcClaim
import eu.europa.ec.eudi.wallet.document.metadata.IssuerMetadata
import java.util.Locale

fun Document.localizedIssuerMetadata(locale: Locale): IssuerMetadata.IssuerDisplay? {
    return issuerMetadata?.issuerDisplay.getLocalizedValue(
        userLocale = locale,
        fallback = null,
        localeExtractor = { it.locale },
        valueExtractor = { it }
    )
}

data class StatusListRef(val idx: Int, val uri: String)

fun IssuedDocument.alertStatusLabel(statusValue: Int): String? =
    data.claims
        .filterIsInstance<SdJwtVcClaim>()
        .find { it.identifier == "status_types" }
        ?.children
        ?.find { it.identifier == statusValue.toString() }
        ?.value as? String

fun IssuedDocument.statusListRef(): StatusListRef? {
    val statusClaim = data.claims
        .filterIsInstance<SdJwtVcClaim>()
        .find { it.identifier == "status" } ?: return null
    val statusListClaim = statusClaim.children
        .find { it.identifier == "status_list" } ?: return null
    val idx = (statusListClaim.children
        .find { it.identifier == "idx" }?.value as? Number)?.toInt() ?: return null
    val uri = statusListClaim.children
        .find { it.identifier == "uri" }?.value as? String ?: return null
    return StatusListRef(idx, uri)
}