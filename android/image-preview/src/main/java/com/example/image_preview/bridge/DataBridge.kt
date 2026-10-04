package com.example.image_preview.bridge

import android.content.Context
import com.example.image_preview.DataShareRequest
import com.example.image_preview.Policy
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object DataBridge {

    private const val PREF_NAME = "consent_data"
    private const val KEY_POLICIES = "policies"

    private val gson = Gson()

    /**
     * Save one consent policy.
     */
    fun savePolicy(
        context: Context,
        policy: Policy
    ) {
        val policies = getPolicies(context).toMutableList()

        val duplicateIndex = policies.indexOfFirst {
            normalize(it.appId) == normalize(policy.appId) &&
                    normalize(it.thirdParty) == normalize(policy.thirdParty) &&
                    normalize(it.data) == normalize(policy.data) &&
                    normalize(it.category) == normalize(policy.category) &&
                    normalize(it.purpose) == normalize(policy.purpose)
        }

        if (duplicateIndex >= 0) {
            policies[duplicateIndex] = policy
        } else {
            policies.add(policy)
        }

        persistPolicies(context, policies)
    }

    /**
     * Save multiple consent policies.
     */
    fun savePolicies(
        context: Context,
        newPolicies: List<Policy>
    ) {
        val policies = getPolicies(context).toMutableList()

        for (policy in newPolicies) {

            val duplicateIndex = policies.indexOfFirst {
                normalize(it.appId) == normalize(policy.appId) &&
                        normalize(it.thirdParty) == normalize(policy.thirdParty) &&
                        normalize(it.data) == normalize(policy.data) &&
                        normalize(it.category) == normalize(policy.category) &&
                        normalize(it.purpose) == normalize(policy.purpose)
            }

            if (duplicateIndex >= 0) {
                policies[duplicateIndex] = policy
            } else {
                policies.add(policy)
            }
        }

        persistPolicies(context, policies)
    }

    /**
     * Return all stored consent policies.
     */
    fun getPolicies(
        context: Context
    ): List<Policy> {

        val prefs = context.getSharedPreferences(
            PREF_NAME,
            Context.MODE_PRIVATE
        )

        val json = prefs.getString(
            KEY_POLICIES,
            null
        ) ?: return emptyList()

        return try {

            val type =
                object : TypeToken<List<Policy>>() {}.type

            gson.fromJson<List<Policy>>(
                json,
                type
            ) ?: emptyList()

        } catch (e: Exception) {

            emptyList()
        }
    }

    /**
     * Find a policy matching the complete
     * data-sharing request.
     */
    fun findMatchingPolicy(
        context: Context,
        request: DataShareRequest
    ): Policy? {

        return getPolicies(context).firstOrNull { policy ->

            policy.allowed &&

                    normalize(policy.appId) ==
                    normalize(request.appId) &&

                    normalize(policy.thirdParty) ==
                    normalize(request.thirdParty) &&

                    normalize(policy.data) ==
                    normalize(request.data) &&

                    normalize(policy.category) ==
                    normalize(request.category) &&

                    purposeContains(
                        policy.purpose,
                        request.purpose
                    )
        }
    }

    /**
     * Validate whether a request has
     * matching user consent.
     */
    fun validateConsent(
        context: Context,
        request: DataShareRequest
    ): Boolean {

        return findMatchingPolicy(
            context,
            request
        ) != null
    }

    /**
     * Delete all stored consent policies.
     *
     * Used for controlled experiments.
     */
    fun clearPolicies(
        context: Context
    ) {

        context.getSharedPreferences(
            PREF_NAME,
            Context.MODE_PRIVATE
        )
            .edit()
            .remove(KEY_POLICIES)
            .apply()
    }

    /**
     * Backward-compatible helper.
     */
    fun getStoredThirdParty(
        context: Context
    ): String? {

        return getPolicies(context)
            .firstOrNull()
            ?.thirdParty
    }

    /**
     * Backward-compatible helper.
     */
    fun getStoredDeveloper(
        context: Context
    ): String? {

        return getPolicies(context)
            .firstOrNull()
            ?.appId
    }

    /**
     * Internal function used to persist the
     * complete policy list.
     */
    private fun persistPolicies(
        context: Context,
        policies: List<Policy>
    ) {

        val json = gson.toJson(policies)

        context.getSharedPreferences(
            PREF_NAME,
            Context.MODE_PRIVATE
        )
            .edit()
            .putString(
                KEY_POLICIES,
                json
            )
            .apply()
    }

    /**
     * Normalize strings before comparison.
     */
    private fun normalize(
        value: String
    ): String {

        return value
            .trim()
            .replace(
                Regex("\\s+"),
                " "
            )
            .lowercase()
    }

    /**
     * Check whether the requested purpose
     * exists inside the DSS declared purposes.
     *
     * Example:
     *
     * DSS:
     * "Analytics, Advertising or marketing"
     *
     * Request:
     * "Analytics"
     *
     * Result:
     * true
     */
    private fun purposeContains(
        declaredPurposes: String,
        requestedPurpose: String
    ): Boolean {

        val requested =
            normalize(requestedPurpose)

        return declaredPurposes
            .split(",")
            .map {
                normalize(it)
            }
            .any {
                it == requested
            }
    }
}