package com.example.image_preview

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore

object FirebaseHelper {

    fun uploadConsent(
        developer: String,
        thirdParty: String,
        category: String,
        purpose: String
    ) {
        Log.d("FIREBASE", "uploadConsent() CALLED")

        val db = FirebaseFirestore.getInstance()

        val consent = hashMapOf(
            "developer" to developer,
            "thirdParty" to thirdParty,
            "category" to category,
            "purpose" to purpose,
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("consents")
            .add(consent)
            .addOnSuccessListener {
                Log.d("FIREBASE", "Consent uploaded")
            }
            .addOnFailureListener { e ->
                Log.e("FIREBASE", "Upload failed", e)
            }
    }
}