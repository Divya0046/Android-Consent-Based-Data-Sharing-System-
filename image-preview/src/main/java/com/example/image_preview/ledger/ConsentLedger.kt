package com.example.image_preview.ledger
import com.example.image_preview.model.ConsentToken


/**
 * Simple in-memory Consent Ledger
 * Stores encrypted consent tokens (crypto-opaque)
 */
object ConsentLedger {

    private val tokens = mutableListOf<ConsentToken>()

    fun store(token: ConsentToken) {
        tokens.add(token)
    }

    /**
     * Ledger does NOT inspect encrypted consent.
     * Validation is abstracted (always true if token exists).
     */
    fun isConsentValid(): Boolean {
        return tokens.isNotEmpty()
    }
}

