package com.example.image_preview.crypto

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object HkdfUtils {

    fun deriveKey(
        masterKey: ByteArray,
        info: String,
        keyLength: Int = 32
    ): ByteArray {

        val mac = Mac.getInstance("HmacSHA256")
        val keySpec = SecretKeySpec(masterKey, "HmacSHA256")
        mac.init(keySpec)

        val prk = mac.doFinal(info.toByteArray())

        return prk.copyOfRange(0, keyLength)
    }

}