package eu.europa.ec.networklogic.repository

import java.math.BigInteger
import java.security.MessageDigest
import java.security.interfaces.ECPublicKey
import java.util.Base64

internal const val INBOX_KEY_ALIAS = "digdir_inbox_signing_key"

internal fun ecPublicKeyJwkCoords(publicKey: ECPublicKey): Pair<String, String> {
    val encoder = Base64.getUrlEncoder().withoutPadding()
    return Pair(
        encoder.encodeToString(publicKey.w.affineX.toFixedBytes()),
        encoder.encodeToString(publicKey.w.affineY.toFixedBytes()),
    )
}

// RFC 7638 canonical form — key order and separators must match the server's _jwk_thumbprint().
internal fun jwkThumbprint(x: String, y: String): String {
    val canonical = """{"crv":"P-256","kty":"EC","x":"$x","y":"$y"}"""
    val hash = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
    return Base64.getUrlEncoder().withoutPadding().encodeToString(hash)
}

// Strips BigInteger sign byte and pads short arrays to the fixed coordinate width.
internal fun BigInteger.toFixedBytes(size: Int = 32): ByteArray {
    val bytes = toByteArray()
    return when {
        bytes.size == size + 1 && bytes[0] == 0.toByte() -> bytes.drop(1).toByteArray()
        bytes.size < size -> ByteArray(size - bytes.size) + bytes
        else -> bytes
    }
}
