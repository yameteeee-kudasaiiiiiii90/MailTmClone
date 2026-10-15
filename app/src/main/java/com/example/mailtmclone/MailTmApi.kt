package com.example.mailtmclone

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

// --- Data Models ---

data class DomainResponse(
    @SerializedName("hydra:member") val domains: List<DomainItem>
)

data class DomainItem(
    val id: String,
    val domain: String,
    val isActive: Boolean = true
)

data class AccountRequest(
    val address: String,
    val password: String
)

data class AccountResponse(
    val id: String,
    val address: String,
    val quota: Long = 40000000L,
    val used: Long = 0L,
    val createdAt: String? = null
)

data class TokenResponse(
    val token: String,
    val id: String
)

data class MessagesResponse(
    @SerializedName("hydra:member") val messages: List<MessageSummary>
)

data class MessageSummary(
    val id: String,
    val from: Sender,
    val subject: String,
    val intro: String?,
    val seen: Boolean,
    val hasAttachments: Boolean = false,
    val size: Int = 0,
    val createdAt: String
)

data class Sender(
    val address: String,
    val name: String?
)

data class AttachmentItem(
    val id: String,
    val filename: String,
    val contentType: String?,
    val size: Int = 0,
    val downloadUrl: String?
)

data class MessageDetail(
    val id: String,
    val from: Sender,
    val to: List<Sender>? = null,
    val subject: String,
    val text: String?,
    val html: List<String>?,
    val hasAttachments: Boolean = false,
    val attachments: List<AttachmentItem>? = null,
    val size: Int = 0,
    val createdAt: String
)

data class MessageSource(
    val id: String,
    val data: String
)

// --- Retrofit API Interface ---

interface MailTmApi {
    @GET("domains")
    suspend fun getDomains(): DomainResponse

    @POST("accounts")
    suspend fun createAccount(@Body request: AccountRequest): AccountResponse

    @POST("token")
    suspend fun getToken(@Body request: AccountRequest): TokenResponse

    @GET("me")
    suspend fun getAccountInfo(@Header("Authorization") bearerToken: String): AccountResponse

    @GET("messages")
    suspend fun getMessages(@Header("Authorization") bearerToken: String): MessagesResponse

    @GET("messages/{id}")
    suspend fun getMessageDetail(
        @Header("Authorization") bearerToken: String,
        @Path("id") id: String
    ): MessageDetail

    @GET("sources/{id}")
    suspend fun getMessageSource(
        @Header("Authorization") bearerToken: String,
        @Path("id") id: String
    ): MessageSource

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
        const val BASE_URL = "https://api.mail.tm/"

        fun create(): MailTmApi {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(MailTmApi::class.java)
        }
    }
}
