package com.example.newapp

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.analytics.FirebaseAnalytics

class SdkPageActivity : AppCompatActivity() {

    private lateinit var firebaseAnalytics: FirebaseAnalytics

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sdk_page)

        Log.d("SDK_PROOF", "SdkPageActivity onCreate reached")

        val analytics = FirebaseAnalytics.getInstance(this)
        analytics.logEvent("sdk_page_opened", null)

        Log.d("SDK_PROOF", "SDK initialized and event sent")
    }

}
