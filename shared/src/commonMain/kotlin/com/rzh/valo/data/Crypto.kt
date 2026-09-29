package com.rzh.valo.data

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** SHA-1 摘要（20 字节），平台实现 */
expect fun sha1(data: ByteArray): ByteArray

/**
 * AES-192-CBC + PKCS7 解密，IV 取 [key] 前 16 字节。
 * 号角接口的 text/plain 响应体是 AES 加密后的 Base64 文本。
 */
expect fun aesCbcDecrypt(key: ByteArray, data: ByteArray): ByteArray

/** Base64（Mime 模式容忍换行），跨平台实现 */
@OptIn(ExperimentalEncodingApi::class)
fun decodeBase64(text: String): ByteArray = Base64.Mime.decode(text.trim())

/** SHA-1 十六进制签名（号角请求头的 x-hj-sign） */
fun sha1Hex(input: String): String =
    sha1(input.encodeToByteArray()).joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
