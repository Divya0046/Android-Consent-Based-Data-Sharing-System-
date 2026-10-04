package com.example.image_preview

data class DataShareRequest(
    val appId: String,
    val thirdParty: String,
    val data: String,
    val category: String,
    val purpose: String
)