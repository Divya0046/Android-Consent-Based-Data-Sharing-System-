package com.example.image_preview.keys

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey

object ThirdPartyKeyProvider {

    private val keyPair: KeyPair by lazy {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048)
        generator.generateKeyPair()
    }

    val publicKey: PublicKey
        get() = keyPair.public
}
