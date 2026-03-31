package com.example.image_preview
import android.content.Context
import com.example.image_preview.model.ValidationRow
import android.util.Log
object PolicyEngine {

    private var storedPolicy: Policy? = null

    fun storeConsent(policy: Policy) {
        storedPolicy = policy
    }

    fun validate(policy: Policy, context: Context): Boolean {

        if (storedPolicy == null) return false

        // STEP 1: existing logic (unchanged)
        val isUserConsentMatching =
            policy.developer.trim().equals(storedPolicy!!.developer.trim(), true) &&
                    policy.thirdParty.trim().equals(storedPolicy!!.thirdParty.trim(), true) &&
                    policy.category.trim().equals(storedPolicy!!.category.trim(), true) &&
                    policy.purpose.trim().equals(storedPolicy!!.purpose.trim(), true)

        if (!isUserConsentMatching) return false

        // STEP 2: NEW → Data Safety check
        val repo = DataSafetyRepository(context)
        val policies = repo.getPolicies()

        val isValidAccordingToDataSafety = policies.any { token ->
                token.developer.trim().equals(policy.developer.trim(), true) &&
                        token.thirdParty.trim().equals(policy.thirdParty.trim(), true) &&
                        token.category.trim().equals(policy.category.trim(), true) &&
                        token.purpose.trim().equals(policy.purpose.trim(), true)
        }

        return isValidAccordingToDataSafety
    }
    fun getValidationTable(policy: Policy, context: Context): Pair<List<ValidationRow>, Boolean> {

        val repo = DataSafetyRepository(context)
        val policies = repo.getPolicies()

        val matchedPolicy = policies.find {
            it.developer.equals(policy.developer, true)
        } ?: return Pair(emptyList(), false)

        val rows = listOf(
            ValidationRow(
                "ThirdParty",
                policy.thirdParty,
                policy.thirdParty.equals(matchedPolicy.thirdParty, true)
            ),
            ValidationRow(
                "Purpose",
                policy.purpose,
                policy.purpose.equals(matchedPolicy.purpose, true)
            ),
            ValidationRow(
                "Category",
                policy.category,
                policy.category.equals(matchedPolicy.category, true)
            )
        )

        val isAllowed = rows.all { it.isMatch }

        return Pair(rows, isAllowed)
    }
}