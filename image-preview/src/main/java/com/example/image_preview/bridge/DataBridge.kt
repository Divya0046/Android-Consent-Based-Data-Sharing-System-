package com.example.image_preview.bridge

import android.content.Context
import com.example.image_preview.PolicyEngine

object DataBridge {

    fun saveConsent(
        context: Context,
        developer: String,
        thirdParty: String,
        category: String,
        purpose: String
    ) {
        val prefs = context.getSharedPreferences("consent_data", Context.MODE_PRIVATE)

        prefs.edit().apply {
            putString("developer", developer)
            putString("thirdParty", thirdParty)
            putString("category", category)
            putString("purpose", purpose)
            apply()
        }
    }

    fun validateConsent(
        context: Context,
        developer: String,
        thirdParty: String,
        category: String,
        purpose: String
    ): Boolean {

        val prefs = context.getSharedPreferences("consent_data", Context.MODE_PRIVATE)

        val savedDeveloper = prefs.getString("developer", null)
        val savedThirdParty = prefs.getString("thirdParty", null)
        val savedCategory = prefs.getString("category", null)
        val savedPurpose = prefs.getString("purpose", null)

        return developer.trim().equals(savedDeveloper?.trim(), true) &&
                thirdParty.trim().equals(savedThirdParty?.trim(), true) &&
                category.trim().equals(savedCategory?.trim(), true) &&
                purpose.trim().equals(savedPurpose?.trim(), true)
    }
    fun getStoredDeveloper(context: Context): String? {

        val prefs = context.getSharedPreferences("consent_data", Context.MODE_PRIVATE)

        return prefs.getString("developer", null)
    }


}



