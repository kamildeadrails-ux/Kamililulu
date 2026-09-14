package com.example.model

enum class ProfileRole {
    BUYER,
    ADMIN
}

data class UserProfile(
    val id: String,
    val name: String,
    val role: ProfileRole = ProfileRole.BUYER,
    val avatarEmoji: String = "👤",
    val colorHex: Long = 0xFF2563EB,
    val savedAddresses: List<String> = emptyList(),
    val preferences: String = "",
    val phone: String = ""
)
