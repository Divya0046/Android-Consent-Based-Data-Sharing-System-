package com.example.image_preview.model

data class ConsentToken(
    val developer: String,
    val thirdParty: String,
    val data: String,
    val category: String,
    val purpose: String,
    var allowed: Boolean
)


