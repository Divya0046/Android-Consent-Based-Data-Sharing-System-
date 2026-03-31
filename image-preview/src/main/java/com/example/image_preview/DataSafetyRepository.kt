package com.example.image_preview

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.example.image_preview.model.ConsentToken

class DataSafetyRepository(private val context: Context) {

    fun getPolicies(): List<ConsentToken> {

        val json = context.assets.open("policies.json")
            .bufferedReader()
            .use { it.readText() }

        val type = object : TypeToken<List<ConsentToken>>() {}.type

        return Gson().fromJson(json, type)
    }
}