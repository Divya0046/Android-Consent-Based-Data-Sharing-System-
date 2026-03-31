package com.example.image_preview.crypto

import java.security.PublicKey
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object CryptoUtils {

    // -------- Generate AES-256 Key --------
    fun generateAESKey(): SecretKey {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        return keyGen.generateKey()
    }

    // -------- AES-GCM Encryption --------
    fun encryptAESGCM(
        data: ByteArray,
        key: SecretKey
    ): Pair<ByteArray, ByteArray> {

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")

        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv)

        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)

        val cipherText = cipher.doFinal(data)

        return Pair(cipherText, iv)
    }

    // -------- RSA-OAEP Key Wrapping --------
    fun encryptRSA(
        data: ByteArray,
        publicKey: PublicKey
    ): ByteArray {

        val cipher = Cipher.getInstance(
            "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
        )
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)

        return cipher.doFinal(data)
    }
}
