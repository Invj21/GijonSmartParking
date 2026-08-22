package es.uniovi.federico.gijonsmartparking.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * Qui costruisco una volta sola il client di rete (Retrofit + Moshi) e lo riuso.
 * Lo tengo come "object" (singleton) perché creare Retrofit ogni volta sarebbe inutile.
 *
 * - Moshi: trasforma il JSON di Overpass (e del mio backend) nelle mie data class.
 * - by lazy: il servizio viene creato solo la prima volta che serve davvero.
 */
object NetworkModule {
    // server Overpass; il path "interpreter" lo aggiunge l'interfaccia ParkingApiService
    private const val BASE_URL = "https://z.overpass-api.de/api/"

    // URL del MIO backend Flask (Task 1). In sviluppo punta all'emulatore Android
    // (10.0.2.2 è l'alias che l'emulatore usa per raggiungere il "localhost" del PC host).
    // Per la demo/deploy reale va sostituito con l'URL pubblico (PythonAnywhere/Docker,
    // vedi backend/README.md).
    private const val BACKEND_BASE_URL = "http://10.0.2.2:5000/"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory()) // così Moshi sa leggere le data class Kotlin
        .build()

    val retrofitService: ParkingApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(ParkingApiService::class.java)
    }

    /**
     * Costruisco il client per il backend proprio con un Interceptor OkHttp che allega
     * "Authorization: Bearer <token>" a ogni richiesta quando c'è un token salvato
     * (stesso schema della slide "API Security"). Il tokenManager arriva da fuori
     * (creato una volta sola in ParkingApplication) perché qui, essendo un object,
     * non ho un Context per crearlo da solo.
     */
    fun createBackendApiService(tokenManager: TokenManager): BackendApiService {
        val authInterceptor = Interceptor { chain ->
            val original = chain.request()
            val token = tokenManager.getToken()
            val request = if (token != null) {
                original.newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            } else {
                original
            }
            chain.proceed(request)
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .build()

        return Retrofit.Builder()
            .baseUrl(BACKEND_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(BackendApiService::class.java)
    }

    /**
     * Il backend risponde con path relativi per le foto (es. "/uploads/xyz.jpg", vedi
     * backend/app.py). Qui li trasformo in URL assoluti da passare a Glide.
     */
    fun resolveBackendUrl(path: String): String {
        if (path.startsWith("http")) return path
        return BACKEND_BASE_URL.trimEnd('/') + path
    }
}
