package com.example.newapp

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import com.example.image_preview.DataSafetyRepository
import com.example.image_preview.DataShareRequest
import com.example.image_preview.DssSharedData
import com.example.image_preview.PolicyEngine
import com.example.image_preview.ThirdPartyRepository
import com.example.image_preview.Policy
import com.example.image_preview.bridge.DataBridge

class MainActivity : AppCompatActivity() {

    private lateinit var thirdPartyInput: EditText
    private lateinit var resultContainer: LinearLayout
    private lateinit var resultText: TextView
    private lateinit var detectedAppText: TextView

    private var detectedPackage: String? = null

    private val handler =
        Handler(Looper.getMainLooper())

    private val detectionRunnable =
        object : Runnable {

            override fun run() {

                detectApplication()

                handler.postDelayed(
                    this,
                    2000L
                )
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        setContentView(
            R.layout.activity_main
        )

        thirdPartyInput =
            findViewById(
                R.id.thirdPartyInput
            )

        resultContainer =
            findViewById(
                R.id.resultContainer
            )

        resultText =
            findViewById(
                R.id.resultText
            )

        detectedAppText =
            findViewById(
                R.id.detectedAppText
            )

        val saveButton =
            findViewById<Button>(
                R.id.btnSaveConsent
            )

        val clearButton =
            findViewById<Button>(
                R.id.btnClearConsent
            )

        saveButton.setOnClickListener {

            generateConsent()
        }

        clearButton.setOnClickListener {

            DataBridge.clearPolicies(
                this
            )

            resultContainer.removeAllViews()

            resultText.text =
                "All consent policies cleared."

            Toast.makeText(
                this,
                "Consent cleared",
                Toast.LENGTH_SHORT
            ).show()
        }

        checkUsageAccessPermission()

        handler.post(
            detectionRunnable
        )
    }

    /**
     * Periodically detect the most recently used application.
     */
    private fun detectApplication() {

        if (!AppDetector.hasUsageAccess(this)) {

            detectedAppText.text =
                "Detected App: Usage Access required"

            return
        }

        val packageName =
            AppDetector.getForegroundApp(this)

        /*
         * IMPORTANT:
         *
         * If detection temporarily returns null,
         * keep the previously detected application.
         */
        if (!packageName.isNullOrBlank()) {

            detectedPackage =
                packageName

            detectedAppText.text =
                "Detected App: $packageName"
        }
    }

    /**
     * Check Usage Access permission.
     */
    private fun checkUsageAccessPermission() {

        if (!AppDetector.hasUsageAccess(this)) {

            detectedAppText.text =
                "Detected App: Usage Access required"

            Toast.makeText(
                this,
                "Please allow Usage Access for Newapp.",
                Toast.LENGTH_LONG
            ).show()

            try {

                startActivity(
                    Intent(
                        Settings.ACTION_USAGE_ACCESS_SETTINGS
                    )
                )

            } catch (e: Exception) {

                e.printStackTrace()
            }
        }
    }

    /**
     * Generate consent using the latest detected application.
     */
    private fun generateConsent() {

        /*
         * IMPORTANT:
         *
         * Perform a fresh detection immediately when
         * Generate Consent is pressed.
         */
        val freshlyDetectedPackage =
            AppDetector.getForegroundApp(
                this
            )

        val packageName =
            freshlyDetectedPackage
                ?: detectedPackage

        if (packageName.isNullOrBlank()) {

            Toast.makeText(
                this,
                "No application detected. Open another app first, then return to Newapp.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        /*
         * Update the UI with the package we are actually using.
         */
        detectedPackage =
            packageName

        detectedAppText.text =
            "Detected App: $packageName"

        val repository =
            DataSafetyRepository(
                this
            )

        val sharedData =
            repository.getSharedDataByPackage(
                packageName
            )

        resultContainer.removeAllViews()

        if (sharedData.isEmpty()) {

            resultText.text =
                "No sharedData declaration found for:\n$packageName"

            Toast.makeText(
                this,
                "The detected application is not present with sharedData in policies.json.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        val detected = ThirdPartyRepository(this).getByPackage(packageName)

        if (detected.isEmpty()) {
            resultText.text = "No third parties discovered for:\n$packageName"
            return
        }

        val names = detected.map { "${it.name}  [${it.detectedBy}]" }.toTypedArray()
        val checked = BooleanArray(detected.size) { false }   // deny by default

        AlertDialog.Builder(this)
            .setTitle("$packageName shares data with:")
            .setMultiChoiceItems(names, checked) { _, i, isChecked -> checked[i] = isChecked }
            .setPositiveButton("Generate consent") { _, _ ->
                val selected = detected.filterIndexed { i, _ -> checked[i] }.map { it.name }
                buildConsent(packageName, detected.map { it.name }, selected, sharedData)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun buildConsent(
        packageName: String,
        allThirdParties: List<String>,
        allowed: List<String>,
        sharedData: List<DssSharedData>
    ) {
        resultContainer.removeAllViews()
        val policies = mutableListOf<Policy>()
        for (tp in allowed) for (dss in sharedData) {
            val p = Policy(packageName, tp, dss.data, dss.category, dss.purpose, dss.optional, true)
            policies.add(p); addPolicyRow(p)
        }
        DataBridge.savePolicies(this, policies)

        // Enforcement proof: every discovered third party requests every declared record.
        var allow = 0; var deny = 0
        val log = StringBuilder()
        for (tp in allThirdParties) for (dss in sharedData) {
            val r = PolicyEngine.validateRequest(
                this,
                DataShareRequest(packageName, tp, dss.data, dss.category, dss.purpose.split(",")[0].trim())
            )
            if (r.allowed) allow++ else deny++
            log.append(if (r.allowed) "ALLOW  " else "DENY   ").append(tp).append(" -> ").append(dss.data).append("\n")
        }
        resultText.text =
            "Consent generated for $packageName\n" +
            "Detected third parties: ${allThirdParties.size}, user-allowed: ${allowed.size}\n" +
            "Policies stored: ${policies.size}\n" +
            "Requests: $allow allowed, $deny denied\n\n" + log
    }

    /**
     * Display one consent policy.
     */
    private fun addPolicyRow(
        policy: Policy
    ) {

        val row =
            TextView(this)

        row.text =
            "Third Party: ${policy.thirdParty}\n" +
                    "Data: ${policy.data}\n" +
                    "Category: ${policy.category}\n" +
                    "Purpose: ${policy.purpose}\n" +
                    "Optional: ${policy.optional}\n" +
                    "Decision: ALLOW\n" +
                    "-------------------------"

        row.textSize =
            15f

        row.setPadding(
            16,
            16,
            16,
            16
        )

        resultContainer.addView(
            row
        )
    }

    override fun onResume() {

        super.onResume()

        /*
         * Immediately try to detect the most recent
         * application after returning to Newapp.
         */
        detectApplication()
    }

    override fun onDestroy() {

        super.onDestroy()

        handler.removeCallbacks(
            detectionRunnable
        )
    }
}