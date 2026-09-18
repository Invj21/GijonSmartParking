package es.uniovi.federico.gijonsmartparking.data

import com.squareup.moshi.Json
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path

/**
 * Interfaccia Retrofit per il MIO backend (Flask), non per Overpass.
 * Stesso schema di ParkingApiService: descrivo solo "com'è fatta" la chiamata.
 *
 * Il path è relativo alla BACKEND_BASE_URL definita in NetworkModule.
 */
interface BackendApiService {

    @POST("auth/register")
    suspend fun registerUser(@Body request: RegisterRequest): RegisterResponse

    @POST("auth/login")
    suspend fun loginUser(@Body request: LoginRequest): LoginResponse

    @POST("auth/google")
    suspend fun loginWithGoogle(@Body request: GoogleAuthRequest): GoogleAuthResponse

    // Response<Unit> invece di Unit: un 204 No Content ha body null, e Retrofit lancia
    // KotlinNullPointerException se il tipo dichiarato è "non-null" (vedi retrofit#3075).
    // Con Response<Unit> evito la conversione automatica e controllo isSuccessful a mano.
    @DELETE("account")
    suspend fun deleteAccount(): retrofit2.Response<Unit>

    // --- Profilo (sezione Account: nome, cognome, indirizzo di casa, foto) ---

    @GET("account")
    suspend fun getAccount(): AccountDto

    @PUT("account")
    suspend fun updateAccount(@Body request: UpdateAccountRequest): AccountDto

    @Multipart
    @PUT("account")
    suspend fun updateAccountWithPhoto(
        @Part("first_name") firstName: RequestBody,
        @Part("last_name") lastName: RequestBody,
        @Part("home_address") homeAddress: RequestBody,
        @Part photo: MultipartBody.Part
    ): AccountDto

    // --- Preferiti ---

    @GET("favorites")
    suspend fun getFavorites(): List<FavoriteDto>

    @POST("favorites")
    suspend fun addFavorite(@Body request: AddFavoriteRequest): FavoriteDto

    // Response<Unit>: stesso motivo di deleteAccount, un 204 ha body null e Retrofit
    // lancerebbe KotlinNullPointerException con un tipo di ritorno "non-null".
    @DELETE("favorites/{parkingId}")
    suspend fun removeFavorite(@Path("parkingId") parkingId: Long): retrofit2.Response<Unit>

    // --- Posizione auto  ---

    @GET("car-location")
    suspend fun getCarLocation(): CarLocationDto

    @POST("car-location")
    suspend fun saveCarLocation(@Body request: SaveCarLocationRequest): CarLocationDto

    @Multipart
    @POST("car-location")
    suspend fun saveCarLocationWithPhoto(
        @Part("lat") lat: RequestBody,
        @Part("lng") lng: RequestBody,
        @Part photo: MultipartBody.Part
    ): CarLocationDto

    @DELETE("car-location")
    suspend fun deleteCarLocation(): retrofit2.Response<Unit>
}

data class RegisterRequest(val email: String, val password: String)

data class RegisterResponse(val id: Long, val email: String, val token: String)

data class LoginRequest(val email: String, val password: String)

data class LoginResponse(val token: String)

data class GoogleAuthRequest(@Json(name = "id_token") val idToken: String)

data class GoogleAuthResponse(val token: String, val email: String)

data class FavoriteDto(
    @Json(name = "parking_id") val parkingId: Long,
    @Json(name = "created_at") val createdAt: String
)

data class AddFavoriteRequest(@Json(name = "parking_id") val parkingId: Long)

data class SaveCarLocationRequest(val lat: Double, val lng: Double)

data class CarLocationDto(
    val lat: Double,
    val lng: Double,
    @Json(name = "photo_url") val photoUrl: String?,
    @Json(name = "saved_at") val savedAt: String
)

data class AccountDto(
    val email: String,
    @Json(name = "first_name") val firstName: String?,
    @Json(name = "last_name") val lastName: String?,
    @Json(name = "home_address") val homeAddress: String?,
    @Json(name = "photo_url") val photoUrl: String?
)

data class UpdateAccountRequest(
    @Json(name = "first_name") val firstName: String?,
    @Json(name = "last_name") val lastName: String?,
    @Json(name = "home_address") val homeAddress: String?
)
