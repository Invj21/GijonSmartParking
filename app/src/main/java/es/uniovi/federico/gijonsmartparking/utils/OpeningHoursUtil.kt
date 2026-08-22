package es.uniovi.federico.gijonsmartparking.utils

import java.util.Calendar

/**
 * Classe di appoggio (la metto in "utils" perché non è né UI né dati) che mi serve
 * per capire se un parcheggio è aperto leggendo il tag "opening_hours" di OpenStreetMap.
 *
 * OSM ha una sintassi degli orari complicatissima, io gestisco i casi più frequenti:
 * "24/7", "Mo-Fr 08:00-20:00", "08:00-22:00" e più intervalli separati da ";".
 * Se l'orario non c'è o non riesco a capirlo faccio finta che sia aperto, così non
 * nascondo un parcheggio solo perché su OSM manca il dato (meglio mostrarlo in più).
 */
object OpeningHoursUtil {

    // Controllo veloce per i parcheggi sempre aperti
    fun is24x7(value: String?): Boolean {
        val v = value?.trim()?.lowercase() ?: return false
        return v == "24/7" || v == "24h" || v == "24 hours" || v.contains("00:00-24:00")
    }

    // "aperto adesso" è solo un caso particolare di "aperto a un certo istante" = ora attuale
    fun isOpenNow(value: String?): Boolean = isOpenAt(value, Calendar.getInstance())

    /** Dice se il parcheggio è aperto nell'istante "time" (lo uso per il filtro data/ora). */
    fun isOpenAt(value: String?, time: Calendar): Boolean {
        // se non so niente lo considero aperto (vedi commento in alto)
        if (value.isNullOrBlank() || value.equals("unknown", true)) return true
        if (is24x7(value)) return true

        val day = time.get(Calendar.DAY_OF_WEEK) // 1 = Domenica ... 7 = Sabato
        val minutes = time.get(Calendar.HOUR_OF_DAY) * 60 + time.get(Calendar.MINUTE)

        var matchedAnyRule = false

        // In OSM le varie regole sono separate dal ";", quindi le esamino una a una
        for (rawRule in value.split(";")) {
            val rule = rawRule.trim()
            if (rule.isEmpty()) continue

            val days = parseDays(rule)
            // se la regola vale solo per certi giorni e oggi non c'è, la salto
            if (days != null && day !in days) continue

            for (range in extractTimeRanges(rule)) {
                matchedAnyRule = true
                if (minutes in range) return true
            }
        }

        // se non sono riuscito a leggere nessun orario, di nuovo meglio dire "aperto"
        return !matchedAnyRule
    }

    /** Tira fuori i giorni coperti dalla regola (es. "Mo-Fr"), null se non ci sono giorni. */
    private fun parseDays(rule: String): Set<Int>? {
        val map = mapOf(
            "mo" to Calendar.MONDAY, "tu" to Calendar.TUESDAY, "we" to Calendar.WEDNESDAY,
            "th" to Calendar.THURSDAY, "fr" to Calendar.FRIDAY, "sa" to Calendar.SATURDAY,
            "su" to Calendar.SUNDAY
        )
        // ordine dei giorni per gestire gli intervalli tipo "Mo-Fr"
        val order = listOf("su", "mo", "tu", "we", "th", "fr", "sa")
        val lower = rule.lowercase()
        val result = mutableSetOf<Int>()

        // regex che riconosce sia un giorno singolo ("Sa") sia un intervallo ("Mo-Fr")
        val dayRegex = Regex("(mo|tu|we|th|fr|sa|su)(\\s*-\\s*(mo|tu|we|th|fr|sa|su))?")
        for (m in dayRegex.findAll(lower)) {
            val start = m.groupValues[1]
            val end = m.groupValues[3]
            if (end.isEmpty()) {
                map[start]?.let { result.add(it) }
            } else {
                // intervallo: parto dal giorno iniziale e vado avanti fino a quello finale
                var i = order.indexOf(start)
                val last = order.indexOf(end)
                if (i >= 0 && last >= 0) {
                    while (true) {
                        map[order[i]]?.let { result.add(it) }
                        if (i == last) break
                        i = (i + 1) % order.size
                    }
                }
            }
        }
        return if (result.isEmpty()) null else result
    }

    /** Trasforma gli orari "08:00-22:00" in intervalli di minuti dalla mezzanotte. */
    private fun extractTimeRanges(rule: String): List<IntRange> {
        val timeRegex = Regex("(\\d{1,2}):(\\d{2})\\s*-\\s*(\\d{1,2}):(\\d{2})")
        val ranges = mutableListOf<IntRange>()
        for (m in timeRegex.findAll(rule)) {
            val start = m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt()
            var end = m.groupValues[3].toInt() * 60 + m.groupValues[4].toInt()
            if (end == 0) end = 24 * 60 // se la chiusura è 00:00 la intendo come mezzanotte
            if (end >= start) {
                ranges.add(start..end)
            } else {
                // caso orario che passa la mezzanotte (es. 22:00-06:00): lo spezzo in due
                ranges.add(start..(24 * 60))
                ranges.add(0..end)
            }
        }
        return ranges
    }
}
