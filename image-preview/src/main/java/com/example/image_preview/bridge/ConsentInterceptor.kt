package com.example.image_preview.bridge

import com.example.image_preview.ledger.ConsentLedger
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class ConsentInterceptor(
    private val purpose: String,
    private val dataCategory: String
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {

        val allowed = ConsentLedger.isConsentValid()


        if (!allowed) {
            throw IOException("CONSENT DENIED: outbound data blocked")
        }

        return chain.proceed(chain.request())
    }
}

