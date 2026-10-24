package es.uniovi.federico.gijonsmartparking.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Coda delle modifiche fatte mentre il server non era raggiungibile (preferiti aggiunti o
 * tolti, posizione dell'auto salvata o rimossa). Servono per non perderle: alla prossima
 * sincronizzazione il repository le invia al backend prima di scaricare i dati del server.
 * Sta in un file di preferenze a parte, separato dalla sessione.
 */
class PendingSyncStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun favoriteAdds(): Set<Long> = readIds(KEY_FAVORITE_ADDS)

    @Synchronized
    fun favoriteRemoves(): Set<Long> = readIds(KEY_FAVORITE_REMOVES)

    @Synchronized
    fun queueFavorite(id: Long, isFavorite: Boolean) {
        val adds = readIds(KEY_FAVORITE_ADDS).toMutableSet()
        val removes = readIds(KEY_FAVORITE_REMOVES).toMutableSet()
        if (isFavorite) {
            adds.add(id)
            removes.remove(id)
        } else {
            removes.add(id)
            adds.remove(id)
        }
        writeIds(adds, removes)
    }

    @Synchronized
    fun resolveFavorite(id: Long) {
        val adds = readIds(KEY_FAVORITE_ADDS).toMutableSet()
        val removes = readIds(KEY_FAVORITE_REMOVES).toMutableSet()
        if (adds.remove(id) or removes.remove(id)) writeIds(adds, removes)
    }

    @Synchronized
    fun isCarUploadPending(): Boolean = prefs.getBoolean(KEY_CAR_UPLOAD, false)

    @Synchronized
    fun setCarUploadPending(pending: Boolean) {
        prefs.edit().putBoolean(KEY_CAR_UPLOAD, pending).apply()
    }

    @Synchronized
    fun isCarDeletePending(): Boolean = prefs.getBoolean(KEY_CAR_DELETE, false)

    @Synchronized
    fun setCarDeletePending(pending: Boolean) {
        prefs.edit().putBoolean(KEY_CAR_DELETE, pending).apply()
    }

    @Synchronized
    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun readIds(key: String): Set<Long> =
        (prefs.getStringSet(key, emptySet()) ?: emptySet()).mapNotNull { it.toLongOrNull() }.toSet()

    private fun writeIds(adds: Set<Long>, removes: Set<Long>) {
        prefs.edit()
            .putStringSet(KEY_FAVORITE_ADDS, adds.map { it.toString() }.toSet())
            .putStringSet(KEY_FAVORITE_REMOVES, removes.map { it.toString() }.toSet())
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "sync_prefs"
        private const val KEY_FAVORITE_ADDS = "favorite_adds"
        private const val KEY_FAVORITE_REMOVES = "favorite_removes"
        private const val KEY_CAR_UPLOAD = "car_upload_pending"
        private const val KEY_CAR_DELETE = "car_delete_pending"
    }
}
