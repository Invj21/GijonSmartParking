package es.uniovi.federico.gijonsmartparking.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import es.uniovi.federico.gijonsmartparking.ParkingApplication
import es.uniovi.federico.gijonsmartparking.R
import es.uniovi.federico.gijonsmartparking.databinding.BottomSheetFiltersBinding

/**
 * Pannello dei filtri che sale dal basso (BottomSheetDialogFragment).
 * Quando si apre carico i filtri attuali, l'utente li modifica e con "Applica" li riscrivo
 * nel ViewModel: siccome è lo STESSO ViewModel della lista/mappa, queste si aggiornano da sole.
 */
class FilterBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: BottomSheetFiltersBinding? = null
    private val binding get() = _binding!!

    // activityViewModels -> condivido il ViewModel con la lista che mi ha aperto
    private val viewModel: ParkingViewModel by activityViewModels {
        ParkingViewModelFactory((requireActivity().application as ParkingApplication).repository)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetFiltersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // mostro i filtri già attivi così l'utente vede da dove parte
        bindCriteria(viewModel.filter.value ?: FilterCriteria())

        // aggiorno l'etichetta "Raggio: X km" mentre si trascina lo slider
        updateRadiusLabel(binding.sliderRadius.value.toInt())
        binding.sliderRadius.addOnChangeListener { _, value, _ ->
            updateRadiusLabel(value.toInt())
        }

        // "Reset": rimetto tutto ai valori di default
        binding.btnReset.setOnClickListener {
            bindCriteria(FilterCriteria())
            updateRadiusLabel(binding.sliderRadius.value.toInt())
        }

        // "Applica": leggo le scelte, le mando al ViewModel e chiudo il pannello
        binding.btnApply.setOnClickListener {
            viewModel.setFilter(readCriteria())
            dismiss()
        }
    }

    /** Sposto i comandi (chip e switch) in modo che riflettano i criteri passati. */
    private fun bindCriteria(c: FilterCriteria) {
        binding.chipGroupFee.check(
            when (c.fee) {
                FeeFilter.ALL -> R.id.chipFeeAll
                FeeFilter.FREE -> R.id.chipFeeFree
                FeeFilter.PAID -> R.id.chipFeePaid
            }
        )
        binding.chipGroupCover.check(
            when (c.cover) {
                CoverFilter.ALL -> R.id.chipCoverAll
                CoverFilter.COVERED -> R.id.chipCoverCovered
                CoverFilter.UNCOVERED -> R.id.chipCoverUncovered
            }
        )
        binding.chipGroupHours.check(
            when (c.hours) {
                HoursFilter.ALL -> R.id.chipHoursAll
                HoursFilter.OPEN_24H -> R.id.chipHours24h
                HoursFilter.OPEN_NOW -> R.id.chipHoursOpenNow
            }
        )
        binding.chipGroupVehicle.check(
            when (c.vehicle) {
                VehicleFilter.ALL -> R.id.chipVehicleAll
                VehicleFilter.CAR -> R.id.chipVehicleCar
                VehicleFilter.MOTORCYCLE -> R.id.chipVehicleMoto
            }
        )
        binding.switchCharging.isChecked = c.onlyCharging
        binding.switchAccessible.isChecked = c.onlyAccessible
        binding.switchPink.isChecked = c.onlyPink
        binding.switchNearby.isChecked = c.nearbyEnabled
        binding.sliderRadius.value = c.radiusKm.coerceIn(1f, 50f) // coerceIn: resto nei limiti dello slider
    }

    /** Trasformo le scelte dell'interfaccia in un oggetto FilterCriteria. */
    private fun readCriteria(): FilterCriteria {
        // checkedChipId mi dà il chip selezionato in ciascun gruppo (selezione singola)
        val fee = when (binding.chipGroupFee.checkedChipId) {
            R.id.chipFeeFree -> FeeFilter.FREE
            R.id.chipFeePaid -> FeeFilter.PAID
            else -> FeeFilter.ALL
        }
        val cover = when (binding.chipGroupCover.checkedChipId) {
            R.id.chipCoverCovered -> CoverFilter.COVERED
            R.id.chipCoverUncovered -> CoverFilter.UNCOVERED
            else -> CoverFilter.ALL
        }
        val hours = when (binding.chipGroupHours.checkedChipId) {
            R.id.chipHours24h -> HoursFilter.OPEN_24H
            R.id.chipHoursOpenNow -> HoursFilter.OPEN_NOW
            else -> HoursFilter.ALL
        }
        val vehicle = when (binding.chipGroupVehicle.checkedChipId) {
            R.id.chipVehicleCar -> VehicleFilter.CAR
            R.id.chipVehicleMoto -> VehicleFilter.MOTORCYCLE
            else -> VehicleFilter.ALL
        }
        return FilterCriteria(
            fee = fee,
            cover = cover,
            hours = hours,
            vehicle = vehicle,
            onlyCharging = binding.switchCharging.isChecked,
            onlyAccessible = binding.switchAccessible.isChecked,
            onlyPink = binding.switchPink.isChecked,
            nearbyEnabled = binding.switchNearby.isChecked,
            radiusKm = binding.sliderRadius.value
        )
    }

    private fun updateRadiusLabel(km: Int) {
        binding.tvRadiusValue.text = getString(R.string.filter_radius_value, km)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        // tag usato quando mostro il bottom sheet, utile per ritrovarlo nel FragmentManager
        const val TAG = "FilterBottomSheet"
    }
}
