package com.example.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    val displayName: String = "",
    val photoURL: String? = null,
    @ServerTimestamp val lastAccessTime: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
