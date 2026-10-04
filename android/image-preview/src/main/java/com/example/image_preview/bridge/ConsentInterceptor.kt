package com.example.image_preview.bridge

import android.content.Context
import com.example.image_preview.DataShareRequest
import com.example.image_preview.PolicyEngine
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class ConsentInterceptor(
    private val context: Context,
    private val appId: String,
    private val thirdParty: String,
    private val data: String,
    private val category: String,
    private val purpose: String
) : Interceptor {

    override fun intercept(
        chain: Interceptor.Chain
    ): Response {

        val request = DataShareRequest(
            appId = appId,
            thirdParty = thirdParty,
            data = data,
            category = category,
            purpose = purpose
        )

        val result =
            PolicyEngine.validateRequest(
                context,
                request
            )

        if (!result.allowed) {

            throw IOException(
                "CONSENT DENIED: ${result.reason}"
            )
        }

        return chain.proceed(
            chain.request()
        )
    }
}