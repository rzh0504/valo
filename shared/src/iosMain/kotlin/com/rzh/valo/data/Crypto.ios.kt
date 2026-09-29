package com.rzh.valo.data

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreCrypto.CCCrypt
import platform.CoreCrypto.CC_SHA1
import platform.CoreCrypto.kCCAlgorithmAES
import platform.CoreCrypto.kCCBlockSizeAES128
import platform.CoreCrypto.kCCDecrypt
import platform.CoreCrypto.kCCKeySizeAES192
import platform.CoreCrypto.kCCOptionPKCS7Padding
import platform.CoreCrypto.kCCSuccess

@OptIn(ExperimentalForeignApi::class)
actual fun sha1(data: ByteArray): ByteArray {
    val digest = UByteArray(20)
    data.toUByteArray().usePinned { src ->
        digest.usePinned { dst ->
            CC_SHA1(src.addressOf(0), data.size.convert(), dst.addressOf(0))
        }
    }
    return digest.asByteArray()
}

@OptIn(ExperimentalForeignApi::class)
actual fun aesCbcDecrypt(key: ByteArray, data: ByteArray): ByteArray {
    val keyPinned = key.toUByteArray()
    val ivPinned = key.copyOfRange(0, 16).toUByteArray()
    val input = data.toUByteArray()
    val out = UByteArray(data.size + kCCBlockSizeAES128.toInt())
    var moved: ULong = 0uL
    val status = memScoped {
        val movedVar = alloc<ULongVar>()
        val st = keyPinned.usePinned { keyRef ->
            ivPinned.usePinned { ivRef ->
                input.usePinned { srcRef ->
                    out.usePinned { outRef ->
                        CCCrypt(
                            kCCDecrypt,
                            kCCAlgorithmAES,
                            kCCOptionPKCS7Padding,
                            keyRef.addressOf(0),
                            kCCKeySizeAES192.convert(),
                            ivRef.addressOf(0),
                            srcRef.addressOf(0),
                            data.size.convert(),
                            outRef.addressOf(0),
                            out.size.convert(),
                            movedVar.ptr,
                        )
                    }
                }
            }
        }
        moved = movedVar.value
        st
    }
    check(status == kCCSuccess) { "AES 解密失败：status=$status" }
    return out.copyOf(moved.toInt()).asByteArray()
}
