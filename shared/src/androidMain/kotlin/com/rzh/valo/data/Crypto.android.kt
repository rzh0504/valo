package com.rzh.valo.data

import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

actual fun sha1(data: ByteArray): ByteArray =
    MessageDigest.getInstance("SHA-1").digest(data)

actual fun aesCbcDecrypt(key: ByteArray, data: ByteArray): ByteArray {
    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    cipher.init(
        Cipher.DECRYPT_MODE,
        SecretKeySpec(key, "AES"),
        IvParameterSpec(key.copyOfRange(0, 16)),
    )
    return cipher.doFinal(data)
}
