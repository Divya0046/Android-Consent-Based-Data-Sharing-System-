package com.example.image_preview

import android.content.Context
import com.example.image_preview.bridge.ConsentInterceptor
import okhttp3.OkHttpClient

object NetworkClient {

    /**
     * Creates an OkHttp client whose outbound requests
     * are protected by the consent policy.
     */
    fun createClient(
        context: Context,
        appId: String,
        thirdParty: String,
        data: String,
        category: String,
        purpose: String
    ): OkHttpClient {

        return OkHttpClient.Builder()
            .addInterceptor(
                ConsentInterceptor(
                    context = context,
                    appId = appId,
                    thirdParty = thirdParty,
                    data = data,
                    category = category,
                    purpose = purpose
                )
            )
            .build()
    }
}