package com.example.image_preview.ledger

data class ConsentEntry(
    val app: String,
    val dataCategory: String?,
    val purpose: String?,
    val dataType: String?,
    val thirdParty: String?,
    val decision: Boolean
)

object ConsentTable {

    private val entries = mutableListOf<ConsentEntry>()

    fun addEntry(
        app: String,
        dataCategory: String?,
        purpose: String?,
        dataType: String?,
        thirdParty: String?,
        decision: Boolean
    ) {
        entries.add(
            ConsentEntry(app, dataCategory, purpose, dataType, thirdParty, decision)
        )
    }

    fun getAllEntries(): List<ConsentEntry> {
        return entries
    }
}