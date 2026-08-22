package es.uniovi.federico.gijonsmartparking.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import es.uniovi.federico.gijonsmartparking.R
import es.uniovi.federico.gijonsmartparking.data.ParkingEntity
import es.uniovi.federico.gijonsmartparking.databinding.ItemParkingBinding
import es.uniovi.federico.gijonsmartparking.utils.ParkingLabels

/**
 * Adapter del RecyclerView della lista parcheggi. Uso ListAdapter (con DiffUtil) invece
 * del RecyclerView.Adapter base perché calcola da solo le differenze tra vecchia e nuova
 * lista e anima solo le righe cambiate: comodo perché la lista cambia di continuo coi filtri.
 *
 * Ricevo due "callback" dal Fragment: una per il tap sulla card (apre il dettaglio) e una
 * per il tap sul cuore (preferiti). L'adapter non sa cosa fanno, le richiama e basta.
 */
class ParkingAdapter(
    private val onItemClicked: (ParkingEntity) -> Unit,
    private val onFavoriteClicked: (ParkingEntity) -> Unit
) : ListAdapter<ParkingEntity, ParkingAdapter.ParkingViewHolder>(ParkingDiffCallback) {

    // creo la "scatola" (ViewHolder) gonfiando il layout della singola riga
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ParkingViewHolder {
        val binding = ItemParkingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ParkingViewHolder(binding)
    }

    // riempio la riga in posizione "position" con i dati del parcheggio
    override fun onBindViewHolder(holder: ParkingViewHolder, position: Int) {
        val parking = getItem(position)
        holder.bind(parking, onItemClicked, onFavoriteClicked)
    }

    // il ViewHolder tiene i riferimenti alle View di una riga, così non rifaccio findViewById ogni volta
    class ParkingViewHolder(private val binding: ItemParkingBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(
            parking: ParkingEntity,
            onItemClicked: (ParkingEntity) -> Unit,
            onFavoriteClicked: (ParkingEntity) -> Unit
        ) {
            val context = binding.root.context
            // se è una moto senza nome, mostro un nome generico tradotto
            binding.tvParkingName.text =
                if (parking.name.isBlank()) context.getString(R.string.moto_parking_generic) else parking.name
            binding.tvParkingCity.text = parking.city
            // uso ParkingLabels per mostrare tipo e tariffa già tradotti nella lingua attiva
            binding.chipType.text = ParkingLabels.type(context, parking.type)
            binding.chipFee.text = ParkingLabels.fee(context, parking.fee)

            // chip "EV" solo se c'è la ricarica elettrica
            binding.chipCharging.visibility =
                if (parking.hasCharging) View.VISIBLE else View.GONE

            // chip accessibilità solo se accessibile in sedia a rotelle
            binding.chipAccessible.visibility =
                if (parking.wheelchair == "yes" || parking.wheelchair == "limited") View.VISIBLE else View.GONE

            // chip "moto" solo per i parcheggi di moto/scooter
            binding.chipMoto.visibility =
                if (parking.vehicle == "motorcycle") View.VISIBLE else View.GONE

            // chip "rosa" solo se ci sono posti riservati a donne in gravidanza / genitori
            binding.chipPink.visibility =
                if (parking.hasPinkParking) View.VISIBLE else View.GONE

            // cuore pieno se è tra i preferiti, vuoto altrimenti
            binding.btnFavorite.setImageResource(
                if (parking.isFavorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border
            )
            binding.btnFavorite.setOnClickListener { onFavoriteClicked(parking) }

            // tap su tutta la card -> apro il dettaglio
            binding.root.setOnClickListener {
                onItemClicked(parking)
            }
        }
    }

    // dice a DiffUtil quando due righe sono "la stessa" e quando il contenuto è cambiato
    object ParkingDiffCallback : DiffUtil.ItemCallback<ParkingEntity>() {
        override fun areItemsTheSame(oldItem: ParkingEntity, newItem: ParkingEntity) =
            oldItem.id == newItem.id // stesso parcheggio = stesso id

        override fun areContentsTheSame(oldItem: ParkingEntity, newItem: ParkingEntity) =
            oldItem == newItem // data class: confronta tutti i campi in automatico
    }
}
