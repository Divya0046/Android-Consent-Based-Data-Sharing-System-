package com.example.image_preview

import android.content.Context
import org.json.JSONArray

data class DssSharedData(
    val data: String,
    val category: String,
    val purpose: String,
    val optional: Boolean
)

class DataSafetyRepository(private val context: Context) {

    private fun readPoliciesJson(): JSONArray {
        return context.assets
            .open("policies.json")
            .bufferedReader()
            .use { reader ->
                JSONArray(reader.readText())
            }
    }

    /**
     * Returns only the data-sharing information declared
     * under sharedData for the specified application.
     *
     * collectedData is intentionally NOT used because
     * this research evaluates third-party data sharing.
     */
    fun getSharedDataByPackage(packageName: String): List<DssSharedData> {

        val jsonArray = readPoliciesJson()
        val result = mutableListOf<DssSharedData>()

        for (i in 0 until jsonArray.length()) {

            val appObject = jsonArray.getJSONObject(i)

            val appId = appObject.optString("appId")

            if (!appId.equals(packageName, ignoreCase = true)) {
                continue
            }

            val appInfo = appObject.optJSONObject("appInfo")
                ?: continue

            val sharedData = appInfo.optJSONArray("sharedData")
                ?: continue

            for (j in 0 until sharedData.length()) {

                val dataObject = sharedData.optJSONObject(j)
                    ?: continue

                val data = dataObject.optString("data").trim()
                val category = dataObject.optString("type").trim()
                val purpose = dataObject.optString("purpose").trim()
                val optional = dataObject.optBoolean("optional", false)

                if (
                    data.isNotEmpty() &&
                    category.isNotEmpty() &&
                    purpose.isNotEmpty()
                ) {
                    result.add(
                        DssSharedData(
                            data = data,
                            category = category,
                            purpose = purpose,
                            optional = optional
                        )
                    )
                }
            }
        }

        return result
    }

    /**
     * Returns all application IDs present in the DSS dataset
     * that contain at least one sharedData entry.
     */
    fun getAppsWithSharedData(): List<String> {

        val jsonArray = readPoliciesJson()
        val result = mutableListOf<String>()

        for (i in 0 until jsonArray.length()) {

            val appObject = jsonArray.getJSONObject(i)

            val appId = appObject.optString("appId").trim()

            if (appId.isEmpty()) {
                continue
            }

            val appInfo = appObject.optJSONObject("appInfo")
                ?: continue

            val sharedData = appInfo.optJSONArray("sharedData")

            if (sharedData != null && sharedData.length() > 0) {
                result.add(appId)
            }
        }

        return result.distinct()
    }
}