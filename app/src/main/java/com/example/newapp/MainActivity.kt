package com.example.newapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.text.Editable
import android.text.TextWatcher
import com.example.image_preview.bridge.DataBridge
import android.widget.LinearLayout
import android.widget.TextView
import com.example.image_preview.PolicyEngine
import com.example.image_preview.Policy
import com.example.image_preview.model.ConsentToken


class MainActivity : AppCompatActivity() {

    private lateinit var thirdPartyInput: EditText
    private lateinit var dataCategoryInput: EditText
    private lateinit var purposeInput: EditText
    private lateinit var grantLocationButton: Button
    private lateinit var grantContactsButton: Button
    private lateinit var saveButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        thirdPartyInput = findViewById(R.id.etThirdParty)
        dataCategoryInput = findViewById(R.id.etDataCategory)
        purposeInput = findViewById(R.id.etPurpose)
        grantLocationButton = findViewById(R.id.btnGrantLocation)
        saveButton = findViewById(R.id.btnSaveConsent)

        // Show location button if purpose = Location
        purposeInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (s.toString().equals("Location", ignoreCase = true)) {
                    grantLocationButton.visibility = View.VISIBLE
                } else {
                    grantLocationButton.visibility = View.GONE
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })


        grantLocationButton.setOnClickListener {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    1001
                )
            } else {
                Toast.makeText(this, "Location Already Granted", Toast.LENGTH_SHORT).show()
            }

            }
        grantContactsButton = findViewById(R.id.btnGrantContacts)

        grantContactsButton.setOnClickListener {
            requestContactsPermission()
        }

        saveButton.setOnClickListener {

            val developerPackage = applicationContext.packageName
            val category = dataCategoryInput.text.toString()

            // 🔹 If category is Contacts → check permission first
            if (category.equals("Contacts", true)) {

                if (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.READ_CONTACTS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    Toast.makeText(this, "Please grant Contacts permission first", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val policy = Policy(
                    developer = "com.example.newapp", // or your package name
                    thirdParty = thirdPartyInput.text.toString(),
                    category = dataCategoryInput.text.toString(),
                    purpose = purposeInput.text.toString()
                )

                val (rows, isAllowed) = PolicyEngine.getValidationTable(policy, this)

                val container = findViewById<LinearLayout>(R.id.resultContainer)
                val resultText = findViewById<TextView>(R.id.resultText)

                container.removeAllViews()

                rows.forEach {

                    val rowLayout = LinearLayout(this)
                    rowLayout.orientation = LinearLayout.HORIZONTAL

                    val field = TextView(this)
                    field.text = it.field
                    field.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

                    val value = TextView(this)
                    value.text = it.value
                    value.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

                    val status = TextView(this)
                    status.text = if (it.isMatch) "✅" else "❌"
                    status.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

                    rowLayout.addView(field)
                    rowLayout.addView(value)
                    rowLayout.addView(status)

                    container.addView(rowLayout)
                }
            }

            // 🔹 If category is Location → check location permission
            if (category.equals("Location", true)) {

                if (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    Toast.makeText(this, "Please grant Location permission first", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }

            // 🔹 Now save consent
            DataBridge.saveConsent(
                this,
                developerPackage,
                thirdPartyInput.text.toString(),
                dataCategoryInput.text.toString(),
                purposeInput.text.toString()
            )

            Toast.makeText(this, "Consent Saved", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {

            1001 -> { // LOCATION
                if (grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED
                ) {
                    Toast.makeText(this, "Location Granted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Location Denied", Toast.LENGTH_SHORT).show()
                }
            }

            2001 -> { // CONTACTS
                if (grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED
                ) {
                    Toast.makeText(this, "Contacts Granted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Contacts Denied", Toast.LENGTH_SHORT).show()
                }
            }

    }


    }
    private fun requestContactsPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_CONTACTS),
                2001
            )

        } else {
            Toast.makeText(this, "Contacts Permission Already Granted", Toast.LENGTH_SHORT).show()
        }
    }
}