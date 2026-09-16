package es.uniovi.federico.gijonsmartparking.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.bumptech.glide.Glide
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import es.uniovi.federico.gijonsmartparking.ParkingApplication
import es.uniovi.federico.gijonsmartparking.R
import es.uniovi.federico.gijonsmartparking.databinding.FragmentFindMyCarBinding
import es.uniovi.federico.gijonsmartparking.notifications.CarReminderScheduler
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Schermata "trova la mia auto": salvo dove ho parcheggiato (posizione GPS + nota + foto),
 * così dopo la ritrovo e mi ci faccio portare. La posizione salvata sta nel database
 * (tramite repository/ViewModel), la foto la salvo come file e ne tengo solo il percorso.
 *
 * Implementa anche la bussola (Task 4): SensorEventListener riceve l'orientamento del
 * telefono, lo combino con la posizione GPS live per calcolare in che direzione ruotare
 * la freccia (CompassArrowView) perché punti verso l'auto parcheggiata.
 */
class FindMyCarFragment : Fragment(), SensorEventListener {

    private var _binding: FragmentFindMyCarBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ParkingViewModel by viewModels {
        ParkingViewModelFactory((requireActivity().application as ParkingApplication).repository)
    }

    // percorso dell'ultima foto scattata (può essere null se non ho fatto foto)
    private var currentPhotoPath: String? = null

    // launcher della fotocamera: quando lo scatto riesce mostro la foto. Se l'utente annulla,
    // il file vuoto creato in anticipo da createImageFile() va cancellato e il riferimento
    // azzerato, altrimenti un "Salva" successivo caricherebbe quel file da 0 byte come foto.
    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            showPhoto(currentPhotoPath, null)
        } else {
            currentPhotoPath?.let { File(it).delete() }
            currentPhotoPath = null
        }
    }

    // launcher del permesso posizione: se l'utente accetta, prendo la posizione (e faccio
    // partire anche gli aggiornamenti live per la bussola, se la schermata è già visibile)
    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            getCurrentLocation()
            startCompassLocationUpdates()
        }
    }

    // launcher del permesso notifiche (Task 6, richiesto da Android 13+): se nego, il
    // promemoria resta comunque programmato ma CarReminderWorker non mostrerà nulla
    // (ricontrolla da solo il permesso al momento di notificare).
    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    // --- Bussola: sensori ---

    private val sensorManager: SensorManager by lazy {
        requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    // sensore "software" TYPE_ROTATION_VECTOR (fonde accelerometro+magnetometro+giroscopio,
    // schema "hardware sensors -> software sensor" visto a teoria); se il dispositivo non
    // ce l'ha, ripiego su accelerometro + magnetometro grezzi.
    private var rotationVectorSensor: Sensor? = null
    private var accelerometerSensor: Sensor? = null
    private var magnetometerSensor: Sensor? = null

    private var lastAccelerometer: FloatArray? = null
    private var lastMagnetometer: FloatArray? = null

    private var currentAzimuthDegrees = 0f

    // --- Bussola: posizione live dell'utente + posizione dell'auto ---

    private val fusedClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(requireActivity())
    }

    private var lastKnownUserLocation: Location? = null
    private var carLocationForBearing: Location? = null

    private val compassLocationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let {
                lastKnownUserLocation = it
                updateCompassArrow()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFindMyCarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (rotationVectorSensor == null) {
            accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            magnetometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        }

        // Provo a riallineare con l'ultima posizione salvata sul backend (Task 3): se offline
        // o non loggato, non succede nulla e resta quella già in Room.
        viewModel.syncCarLocation()

        // Osservo la posizione salvata: se c'è mostro i dettagli, se non c'è mostro l'invito a salvare.
        // Così l'interfaccia si aggiorna da sola appena salvo o cancello (LiveData dal DB).
        viewModel.carLocation.observe(viewLifecycleOwner) { car ->
            if (car != null) {
                binding.tvStatus.text = getString(R.string.car_parked_status)

                val date = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(car.timestamp)
                val lat = String.format("%.5f", car.latitude)
                val lon = String.format("%.5f", car.longitude)
                val detailText = listOf(
                    getString(R.string.label_note, car.note),
                    getString(R.string.label_date, date),
                    getString(R.string.label_position, lat, lon)
                ).joinToString("\n")
                binding.tvCarDetails.text = detailText

                binding.btnDeleteLocation.visibility = View.VISIBLE
                binding.btnGetDirections.visibility = View.VISIBLE
                binding.tvCompassHint.visibility = View.VISIBLE
                binding.compassArrowView.visibility = View.VISIBLE

                carLocationForBearing = Location("car_location").apply {
                    latitude = car.latitude
                    longitude = car.longitude
                }
                updateCompassArrow()

                showPhoto(car.imagePath, car.remotePhotoUrl)

                // "portami all'auto": Intent verso Google Maps in modalità navigazione
                binding.btnGetDirections.setOnClickListener {
                    val uri = Uri.parse("google.navigation:q=${car.latitude},${car.longitude}")
                    val intent = Intent(Intent.ACTION_VIEW, uri)
                    intent.setPackage("com.google.android.apps.maps")
                    startActivity(intent)
                }
            } else {
                binding.tvStatus.text = getString(R.string.no_car_saved)
                binding.tvCarDetails.text = getString(R.string.save_location_hint)
                binding.ivCarPhoto.visibility = View.GONE
                binding.btnDeleteLocation.visibility = View.GONE
                binding.btnGetDirections.visibility = View.GONE
                binding.tvCompassHint.visibility = View.GONE
                binding.compassArrowView.visibility = View.GONE
                carLocationForBearing = null
                binding.etNote.setText("")
            }
        }

        binding.btnSaveLocation.setOnClickListener { checkPermissionsAndGetLocation() }
        binding.btnTakePhoto.setOnClickListener { dispatchTakePictureIntent() }
        binding.btnDeleteLocation.setOnClickListener {
            viewModel.deleteCarLocation()
            currentPhotoPath = null
            CarReminderScheduler.cancel(requireContext())
        }
    }

    override fun onResume() {
        super.onResume()
        // Registro i sensori/la posizione solo mentre la schermata è visibile (lifecycle-aware,
        // come richiesto a teoria): niente batteria sprecata quando sono su un'altra pagina.
        val rotation = rotationVectorSensor
        if (rotation != null) {
            sensorManager.registerListener(this, rotation, SensorManager.SENSOR_DELAY_UI)
        } else {
            accelerometerSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
            magnetometerSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        }
        startCompassLocationUpdates()
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        fusedClient.removeLocationUpdates(compassLocationCallback)
    }

    @SuppressLint("MissingPermission")
    private fun startCompassLocationUpdates() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L).build()
        fusedClient.requestLocationUpdates(request, compassLocationCallback, Looper.getMainLooper())
    }

    // --- SensorEventListener: qui arriva l'orientamento del telefono ---

    override fun onSensorChanged(event: SensorEvent) {
        val rotationMatrix = FloatArray(9)

        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                applyRotationMatrix(rotationMatrix)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                lastAccelerometer = event.values.clone()
                applyFallbackOrientation(rotationMatrix)
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                lastMagnetometer = event.values.clone()
                applyFallbackOrientation(rotationMatrix)
            }
        }
    }

    // fallback per i dispositivi senza TYPE_ROTATION_VECTOR: combino accelerometro+magnetometro
    private fun applyFallbackOrientation(rotationMatrix: FloatArray) {
        val accel = lastAccelerometer ?: return
        val mag = lastMagnetometer ?: return
        if (SensorManager.getRotationMatrix(rotationMatrix, null, accel, mag)) {
            applyRotationMatrix(rotationMatrix)
        }
    }

    private fun applyRotationMatrix(rotationMatrix: FloatArray) {
        val orientationAngles = FloatArray(3)
        SensorManager.getOrientation(rotationMatrix, orientationAngles)

        var azimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
        if (azimuth < 0) azimuth += 360f
        currentAzimuthDegrees = azimuth

        updateCompassArrow()
    }

    /**
     * Angolo finale della freccia = bearing (direzione bussola verso l'auto, da
     * Location.bearingTo) meno l'azimuth del telefono (dove sto puntando io adesso).
     * Se non ho ancora né la posizione utente né quella dell'auto, non disegno nulla.
     */
    private fun updateCompassArrow() {
        val userLocation = lastKnownUserLocation ?: return
        val carLocation = carLocationForBearing ?: return

        val bearing = userLocation.bearingTo(carLocation) // gradi, 0-360 rispetto al nord
        var arrowAngle = bearing - currentAzimuthDegrees
        if (arrowAngle < 0) arrowAngle += 360f

        binding.compassArrowView.angleDegrees = arrowAngle
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // non mi serve reagire ai cambi di precisione del sensore
    }

    // preparo il file dove salvare la foto e lancio la fotocamera con un Intent
    private fun dispatchTakePictureIntent() {
        val photoFile: File? = try { createImageFile() } catch (ex: Exception) { null }
        photoFile?.also {
            // FileProvider: condivido il file con l'app fotocamera in modo sicuro (niente file:// diretto)
            val photoURI: Uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", it)
            takePictureLauncher.launch(photoURI)
        }
    }

    // creo un file immagine vuoto nella cartella privata dell'app (storage esterno specifico)
    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File? = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir).apply {
            currentPhotoPath = absolutePath // mi salvo il percorso per dopo
        }
    }

    /**
     * Mostro la foto del posto auto: preferisco il file LOCALE (quello appena scattato su
     * questo dispositivo), altrimenti quella sincronizzata dal backend (Task 3: posizione
     * salvata da un altro dispositivo, o app reinstallata). Uso Glide (già tra le dipendenze
     * del progetto) invece di BitmapFactory perché carica sia file locali sia URL remoti con
     * la stessa chiamata, in background.
     */
    private fun showPhoto(localPath: String?, remoteUrl: String?) {
        val model: Any? = when {
            localPath != null -> File(localPath)
            remoteUrl != null -> remoteUrl
            else -> null
        }
        if (model != null) {
            binding.ivCarPhoto.visibility = View.VISIBLE
            Glide.with(this).load(model).centerCrop().into(binding.ivCarPhoto)
        } else {
            binding.ivCarPhoto.visibility = View.GONE
        }
    }

    // controllo il permesso posizione; se ce l'ho prendo la posizione, altrimenti lo chiedo
    private fun checkPermissionsAndGetLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            getCurrentLocation()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    @SuppressLint("MissingPermission")
    private fun getCurrentLocation() {
        // lastLocation è spesso null (cache vuota, GPS appena attivato): in quel caso
        // richiediamo una posizione "fresca" così il salvataggio funziona sempre.
        fusedClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                saveLocation(location.latitude, location.longitude)
            } else {
                requestFreshLocation()
            }
        }.addOnFailureListener {
            requestFreshLocation()
        }
    }

    // chiedo una posizione nuova di zecca (più lenta ma affidabile) quando lastLocation è null
    @SuppressLint("MissingPermission")
    private fun requestFreshLocation() {
        fusedClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            CancellationTokenSource().token
        ).addOnSuccessListener { fresh ->
            if (fresh != null) {
                saveLocation(fresh.latitude, fresh.longitude)
            } else {
                Toast.makeText(context, getString(R.string.location_unavailable), Toast.LENGTH_LONG).show()
            }
        }.addOnFailureListener {
            Toast.makeText(context, getString(R.string.location_unavailable), Toast.LENGTH_LONG).show()
        }
    }

    // salvo posizione + nota + (eventuale) foto nel database tramite il ViewModel
    private fun saveLocation(lat: Double, lon: Double) {
        val note = binding.etNote.text.toString()
        viewModel.saveCarLocation(lat, lon, note, currentPhotoPath)
        Toast.makeText(context, getString(R.string.toast_location_saved), Toast.LENGTH_SHORT).show()

        // Task 6: programmo il promemoria "non dimenticare l'auto" e, se serve, chiedo il
        // permesso per le notifiche (Android 13+).
        requestNotificationPermissionIfNeeded()
        CarReminderScheduler.schedule(requireContext(), System.currentTimeMillis())
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
