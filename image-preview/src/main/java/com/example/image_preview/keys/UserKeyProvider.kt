package com.example.image_preview.keys

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey

object UserKeyProvider {

    val keyPair: KeyPair = generateKeyPair()

    val privateKey: PrivateKey
        get() = keyPair.private

    val publicKey: PublicKey
        get() = keyPair.public

    // 🔑 RSA key generation
    private fun generateKeyPair(): KeyPair {
        val keyGen = KeyPairGenerator.getInstance("RSA")
        keyGen.initialize(2048)
        return keyGen.generateKeyPair()
    }
}




