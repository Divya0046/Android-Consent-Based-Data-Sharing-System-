package com.example.image_preview

data class Policy(
    val appId: String,
    val thirdParty: String,
    val data: String,
    val category: String,
    val purpose: String,
    val optional: Boolean,
    val allowed: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)