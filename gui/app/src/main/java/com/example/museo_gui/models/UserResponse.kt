package com.example.museo_gui.models

data class UsersResponse(
    val message: String,
    val total_users: Int,
    val users: List<User>
)