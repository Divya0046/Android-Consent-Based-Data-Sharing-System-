package com.example.newapp

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.example.newapp.IPolicyService
import com.example.image_preview.bridge.DataBridge

class PolicyValidationService : Service() {

    private val binder = object : IPolicyService.Stub() {

        override fun validatePolicy(
            thirdParty: String?
        ): Boolean {

            if (thirdParty == null) return false

            val storedThirdParty =
                DataBridge.getStoredThirdParty(
                    this@PolicyValidationService
                )

            android.util.Log.d(
                "VALIDATION_DEBUG",
                "Input = ${thirdParty.trim()}"
            )

            android.util.Log.d(
                "VALIDATION_DEBUG",
                "Stored = ${storedThirdParty?.trim()}"
            )

            return thirdParty.trim()
                .equals(storedThirdParty?.trim(), true)
        }

        override fun getStoredDeveloper(): String? {

            return packageName
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
}