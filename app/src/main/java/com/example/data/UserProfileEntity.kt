package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.ProfileRole
import com.example.model.UserProfile
import org.json.JSONArray

@Entity(tableName = "profiles")
data class UserProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val role: String,
    val avatarEmoji: String,
    val colorHex: Long,
    val savedAddressesJson: String,
    val preferences: String,
    val phone: String
) {
    fun toDomain(): UserProfile {
        val addresses = mutableListOf<String>()
        try {
            val jsonArray = JSONArray(savedAddressesJson)
            for (i in 0 until jsonArray.length()) {
                addresses.add(jsonArray.getString(i))
            }
        } catch (e: Exception) {
            // fallback if empty
        }

        val parsedRole = try {
            ProfileRole.valueOf(role)
        } catch (e: Exception) {
            ProfileRole.BUYER
        }

        return UserProfile(
            id = id,
            name = name,
            role = parsedRole,
            avatarEmoji = avatarEmoji,
            colorHex = colorHex,
            savedAddresses = addresses,
            preferences = preferences,
            phone = phone
        )
    }

    companion object {
        fun fromDomain(profile: UserProfile): UserProfileEntity {
            val jsonArray = JSONArray()
            profile.savedAddresses.forEach { jsonArray.put(it) }
            return UserProfileEntity(
                id = profile.id,
                name = profile.name,
                role = profile.role.name,
                avatarEmoji = profile.avatarEmoji,
                colorHex = profile.colorHex,
                savedAddressesJson = jsonArray.toString(),
                preferences = profile.preferences,
                phone = profile.phone
            )
        }
    }
}
