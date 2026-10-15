package com.example.mailtmclone

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

data class DomainResponse(@SerializedName("hydra:member") val domains: List<DomainItem>)
data class DomainItem(val domain: String)

data class AccountRequest(val address: String, val password: String)
data class AccountResponse(val id: String, val address: String)

data class TokenResponse(val token: String, val id: String)

data class MessagesResponse(@SerializedName("hydra:member") val messages: List<MessageSummary>)
data class MessageSummary(
    val id: String,
    val from: Sender,
    val subject: String,
    val intro: String,
    val seen: Boolean,
    val createdAt: String
)
data class Sender(val address: String, val name: String?)

data class MessageDetail(
    val id: String,
    val from: Sender,
    val subject: String,
    val text: String?,
    val html: List<String>?,
    val createdAt: String
)

interface MailTmApi {
    @GET("domains")
    suspend fun getDomains(): DomainResponse

    @POST("accounts")
    suspend fun createAccount(@Body request: AccountRequest): AccountResponse

    @POST("token")
    suspend fun getToken(@Body request: AccountRequest): TokenResponse

    @GET("messages")
    suspend fun getMessages(@Header("Authorization") bearerToken: String): MessagesResponse

    @GET("messages/{id}")
    suspend fun getMessageDetail(
        @Header("Authorization") bearerToken: String,
        @Path("id") id: String
    ): MessageDetail

    @PATCH("messages/{id}")
    @Headers("Content-Type: application/merge-patch+json")
    suspend fun markAsRead(
        @Header("Authorization") bearerToken: String,
        @Path("id") id: String,
        @Body body: Map<String, Boolean> = mapOf("seen" to true)
    ): Response<Unit>

    @DELETE("messages/{id}")
    suspend fun deleteMessage(
        @Header("Authorization") bearerToken: String,
        @Path("id") id: String
    ): Response<Unit>

    @DELETE("accounts/{id}")
    suspend fun deleteAccount(
        @Header("Authorization") bearerToken: String,
        @Path("id") id: String
    ): Response<Unit>

    companion object {
        fun create(): MailTmApi {
            return Retrofit.Builder()
                .baseUrl("https://api.mail.tm/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(MailTmApi::class.java)
        }
    }
}
