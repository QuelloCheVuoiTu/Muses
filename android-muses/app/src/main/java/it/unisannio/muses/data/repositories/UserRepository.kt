package it.unisannio.muses.data.repositories

import it.unisannio.muses.api.RetrofitInstance
import it.unisannio.muses.data.models.User
import it.unisannio.muses.data.models.CreateUserRequest
import it.unisannio.muses.data.models.UpdateUserRequest
import retrofit2.Response
import com.google.gson.JsonElement

class UserRepository {
	suspend fun getUser(userId: String): Response<User> {
		return RetrofitInstance.api.getUser(userId)
	}

	suspend fun createUser(user: CreateUserRequest): Response<Unit> {
		return RetrofitInstance.api.createUser(user)
	}

	suspend fun updateUser(userId: String, user: UpdateUserRequest): Response<Unit> {
		return RetrofitInstance.api.updateUser(userId, user)
	}

	suspend fun getPreferences(userId: String): Response<JsonElement> {
		return RetrofitInstance.api.getPreferences(userId)
	}

	suspend fun patchPreferences(userId: String, body: com.google.gson.JsonObject): Response<Unit> {
		return RetrofitInstance.api.patchPreferences(userId, body)
	}
}