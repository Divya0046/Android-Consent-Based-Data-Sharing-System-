package com.example.image_preview

import android.content.Context
import org.json.JSONObject

data class DetectedThirdParty(
    val name: String,
    val detectedBy: String,   // static | dynamic | both | manual-runtime
    val firstParty: Boolean
)

/** Reads third parties discovered by the offline study (assets/third_parties.json). */
class ThirdPartyRepository(private val context: Context) {

    fun getByPackage(packageName: String): List<DetectedThirdParty> {
        val text = context.assets.open("third_parties.json")
            .bufferedReader().use { it.readText() }
        val arr = JSONObject(text).optJSONArray(packageName) ?: return emptyList()
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            DetectedThirdParty(o.getString("name"), o.getString("detectedBy"), o.optBoolean("firstParty", false))
        }.filter { !it.firstParty }
    }
}
