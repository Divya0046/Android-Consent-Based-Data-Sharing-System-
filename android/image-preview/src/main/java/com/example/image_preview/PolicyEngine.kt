package com.example.image_preview

import android.content.Context
import android.util.Log
import com.example.image_preview.bridge.DataBridge
import com.example.image_preview.model.ValidationRow

data class ValidationResult(
    val allowed: Boolean,
    val reason: String,
    val latencyNs: Long,
    val rows: List<ValidationRow>
)

object PolicyEngine {

    private const val TAG = "PolicyEngine"

    /**
     * Main authorization function.
     *
     * A request is allowed only when:
     *
     * 1. Matching user consent exists.
     * 2. App ID matches.
     * 3. Third party matches.
     * 4. Data matches.
     * 5. Category matches.
     * 6. Purpose matches.
     * 7. The same sharing attributes are present in
     *    the application's sharedData DSS information.
     */
    fun validateRequest(
        context: Context,
        request: DataShareRequest
    ): ValidationResult {

        val startTime = System.nanoTime()

        val repository = DataSafetyRepository(context)

        val dssRecords =
            repository.getSharedDataByPackage(request.appId)

        val storedPolicies =
            DataBridge.getPolicies(context)

        val matchingConsent =
            storedPolicies.firstOrNull { policy ->

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

        val consentExists = matchingConsent != null

        val matchingDssRecord =
            dssRecords.firstOrNull { dss ->

                normalize(dss.data) ==
                        normalize(request.data) &&

                        normalize(dss.category) ==
                        normalize(request.category) &&

                        purposeContains(
                            dss.purpose,
                            request.purpose
                        )
            }

        val dssMatches = matchingDssRecord != null

        val appMatches =
            matchingConsent?.appId?.let {
                normalize(it) == normalize(request.appId)
            } ?: false

        val thirdPartyMatches =
            matchingConsent?.thirdParty?.let {
                normalize(it) == normalize(request.thirdParty)
            } ?: false

        val dataMatches =
            matchingConsent?.data?.let {
                normalize(it) == normalize(request.data)
            } ?: false

        val categoryMatches =
            matchingConsent?.category?.let {
                normalize(it) == normalize(request.category)
            } ?: false

        val purposeMatches =
            matchingConsent?.purpose?.let {
                purposeContains(
                    it,
                    request.purpose
                )
            } ?: false

        val allowed =
            consentExists &&
                    appMatches &&
                    thirdPartyMatches &&
                    dataMatches &&
                    categoryMatches &&
                    purposeMatches &&
                    dssMatches

        val reason = when {

            !consentExists ->
                "No matching user consent"

            !appMatches ->
                "Application mismatch"

            !thirdPartyMatches ->
                "Third-party mismatch"

            !dataMatches ->
                "Data mismatch"

            !categoryMatches ->
                "Category mismatch"

            !purposeMatches ->
                "Purpose mismatch"

            !dssMatches ->
                "Data-sharing attributes are not declared in sharedData"

            else ->
                "All authorization checks passed"
        }

        val latency =
            System.nanoTime() - startTime

        Log.d(
            TAG,
            "Decision=$allowed " +
                    "thirdParty=${request.thirdParty} " +
                    "data=${request.data} " +
                    "category=${request.category} " +
                    "purpose=${request.purpose} " +
                    "latencyNs=$latency " +
                    "reason=$reason"
        )

        val rows = listOf(

            ValidationRow(
                field = "Application",
                value = request.appId,
                isMatch = appMatches
            ),

            ValidationRow(
                field = "Third Party",
                value = request.thirdParty,
                isMatch = thirdPartyMatches
            ),

            ValidationRow(
                field = "Data",
                value = request.data,
                isMatch = dataMatches && dssMatches
            ),

            ValidationRow(
                field = "Category",
                value = request.category,
                isMatch = categoryMatches && dssMatches
            ),

            ValidationRow(
                field = "Purpose",
                value = request.purpose,
                isMatch = purposeMatches && dssMatches
            )
        )

        return ValidationResult(
            allowed = allowed,
            reason = reason,
            latencyNs = latency,
            rows = rows
        )
    }

    /**
     * Generates a validation table for UI display.
     */
    fun getValidationTable(
        context: Context,
        request: DataShareRequest
    ): Pair<List<ValidationRow>, Boolean> {

        val result =
            validateRequest(
                context,
                request
            )

        return Pair(
            result.rows,
            result.allowed
        )
    }

    private fun normalize(value: String): String {

        return value
            .trim()
            .replace(Regex("\\s+"), " ")
            .lowercase()
    }

    private fun purposeContains(
        declaredPurposes: String,
        requestedPurpose: String
    ): Boolean {

        val requested =
            normalize(requestedPurpose)

        return declaredPurposes
            .split(",")
            .map { normalize(it) }
            .any { it == requested }
    }
}