package com.efojug.sleepy.network

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url

@Serializable
data class DeviceStatus(
    val secret: String,
    val device: Int,
    val status: Int,
    val app: String
)

interface ApiService {
    @POST
    suspend fun postStatus(
        @Url fullUrl: String,
        @Body status: DeviceStatus
    ): Response<Unit>
}