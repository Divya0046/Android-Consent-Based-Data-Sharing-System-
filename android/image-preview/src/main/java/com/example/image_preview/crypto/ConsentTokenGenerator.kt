package com.example.image_preview.crypto

import com.example.image_preview.keys.CompanyKeyProvider
import com.example.image_preview.keys.ThirdPartyKeyProvider
import com.example.image_preview.model.ConsentToken
import javax.crypto.SecretKey

object ConsentTokenGenerator {

   /* fun generate(consentData: ByteArray): ConsentToken {

        // STEP 1: Generate AES Key
        val aesKey: SecretKey = CryptoUtils.generateAESKey()

        // STEP 2: Encrypt data with AES-GCM
        val (cipherText, iv) =
            CryptoUtils.encryptAESGCM(consentData, aesKey)

        // STEP 3: Wrap AES key with Company Public Key
        val wrappedKeyCompany =
            CryptoUtils.encryptRSA(
                aesKey.encoded,
                CompanyKeyProvider.publicKey
            )

        // STEP 4: Wrap again with Third Party Public Key
        val wrappedKeyThirdParty =
            CryptoUtils.encryptRSA(
                wrappedKeyCompany,
                ThirdPartyKeyProvider.publicKey
            )

        // STEP 5: Return final encrypted package
       return ConsentToken(
            cipherText = cipherText,
            iv = iv,
            wrappedKey = wrappedKeyThirdParty
        )
    }*/
}

