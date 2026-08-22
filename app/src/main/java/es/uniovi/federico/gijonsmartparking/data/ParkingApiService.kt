package es.uniovi.federico.gijonsmartparking.data

import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Headers

/**
 * Interfaccia di Retrofit per chiamare l'API Overpass (come negli esempi di teoria su
 * Retrofit). Io descrivo solo "com'è fatta" la chiamata e Retrofit scrive il codice.
 *
 * - @GET("interpreter") -> endpoint dell'API
 * - @Query("data") -> la query Overpass viene messa nell'URL come parametro "data"
 * - suspend -> la chiamata di rete deve girare fuori dal thread principale (coroutine)
 * - lo User-Agent è richiesto da Overpass per non rifiutare le richieste anonime
 */
interface ParkingApiService {

    @Headers(
        "Accept: application/json",
        "User-Agent: AsturiasParkingExplorer/1.0 (federico.uniovi.es)"
    )
    @GET("interpreter")
    suspend fun getAsturiasParking(
        @Query("data") query: String
    ): OverpassResponse
}
