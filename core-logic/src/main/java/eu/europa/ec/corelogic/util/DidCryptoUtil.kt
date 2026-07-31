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

package eu.europa.ec.corelogic.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.encodeToString

/**
 * Utility for DID-related cryptographic operations, specifically canonicalization.
 */
object DidCryptoUtil {

    private val compactJson = Json {
        prettyPrint = false
        encodeDefaults = true
    }

    /**
     * Serializer that sorts keys alphabetically for JsonObjects.
     */
    object AlphabeticalSerializer : JsonTransformingSerializer<JsonObject>(JsonObject.serializer()) {
        override fun transformSerialize(element: JsonElement): JsonElement {
            return if (element is JsonObject) {
                val sortedMap = element.toSortedMap()
                val newContent = sortedMap.mapValues { (_, value) ->
                    if (value is JsonObject) transformSerialize(value) else value
                }
                JsonObject(newContent)
            } else {
                element
            }
        }
    }

    /**
     * Canonicalizes a JsonObject according to the server's requirements:
     * - Sorted keys
     * - No whitespace
     */
    fun canonicalize(jsonObject: JsonObject): String {
        return compactJson.encodeToString(AlphabeticalSerializer, jsonObject)
    }
}
