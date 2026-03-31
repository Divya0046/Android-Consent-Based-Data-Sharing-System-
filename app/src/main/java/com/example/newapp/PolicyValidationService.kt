package com.example.newapp

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.example.newapp.IPolicyService
import com.example.image_preview.bridge.DataBridge

class PolicyValidationService : Service() {

    private val binder: IBinder = object : IPolicyService.Stub() {

        override fun validatePolicy(
            developer: String?,
            thirdParty: String?,
            category: String?,
            purpose: String?
        ): Boolean {

            if (developer == null || thirdParty == null ||
                category == null || purpose == null
            ) return false

            return DataBridge.validateConsent(
                this@PolicyValidationService,
                developer,
                thirdParty,
                category,
                purpose
            )
        }

        override fun getStoredDeveloper(): String? {
            return DataBridge.getStoredDeveloper(this@PolicyValidationService)
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
}