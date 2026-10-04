package com.example.image_preview.ledger

import android.content.Context
import com.example.image_preview.DataShareRequest
import com.example.image_preview.Policy
import com.example.image_preview.bridge.DataBridge

object ConsentLedger {

    fun store(
        context: Context,
        policy: Policy
    ) {

        DataBridge.savePolicy(
            context,
            policy
        )
    }

    fun getAll(
        context: Context
    ): List<Policy> {

        return DataBridge.getPolicies(
            context
        )
    }

    fun validate(
        context: Context,
        request: DataShareRequest
    ): Boolean {

        return DataBridge.validateConsent(
            context,
            request
        )
    }

    fun clear(
        context: Context
    ) {

        DataBridge.clearPolicies(
            context
        )
    }
}