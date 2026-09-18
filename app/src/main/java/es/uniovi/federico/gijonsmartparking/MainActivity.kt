package es.uniovi.federico.gijonsmartparking

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * L'unica Activity dell'app (single-activity): fa solo da contenitore. Tutte le schermate sono Fragment
 * dentro il NavHostFragment, e la barra in basso permette di spostarsi tra le sezioni.
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Prendo il NavController dal NavHostFragment: è lui che gestisce la navigazione
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        // Login multi-utente: scelgo la start destination in base al token
        // salvato. Il grafo NON è impostato via app:navGraph nel layout apposta, così
        // lo costruisco qui prima che venga mostrato nulla
        val app = application as ParkingApplication
        val startDestination =
            if (app.tokenManager.hasToken()) R.id.homeFragment else R.id.loginFragment
        navController.graph = navController.navInflater.inflate(R.navigation.nav_graph).apply {
            setStartDestination(startDestination)
        }

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        // Gestisco i tap della barra a mano perché voglio che toccando "Home" si torni sempre alla home anche quando sono
        // dentro una pagina figlia.
        bottomNav.setOnItemSelectedListener { item ->
            navigateToTab(navController, item.itemId)
            true
        }
        bottomNav.setOnItemReselectedListener { item ->
            navigateToTab(navController, item.itemId)
        }

        // Quando cambia schermata aggiorno la voce evidenziata nella barra (es. dopo il
        // tasto indietro). Le pagine figlie non sono nel menu, quindi findItem torna null.
        // Nascondo anche la barra su login/registrazione: senza account non deve essere
        // possibile saltare direttamente a Home/Mappa/Trova auto toccando un tab.
        navController.addOnDestinationChangedListener { _, destination, _ ->
            bottomNav.menu.findItem(destination.id)?.isChecked = true
            bottomNav.visibility =
                if (destination.id == R.id.loginFragment || destination.id == R.id.registerFragment) {
                    View.GONE
                } else {
                    View.VISIBLE
                }
        }
    }

    /** Se la destinazione è già nel back stack ci torno (pop), altrimenti ci navigo da zero. */
    private fun navigateToTab(
        navController: androidx.navigation.NavController,
        destinationId: Int
    ) {
        if (navController.currentDestination?.id == destinationId) return
        val popped = navController.popBackStack(destinationId, inclusive = false)
        if (!popped) {
            // non era nello stack: navigo pulendo fino alla home, così non accumulo schermate
            val options = NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setPopUpTo(navController.graph.startDestinationId, /* inclusive = */ false)
                .build()
            navController.navigate(destinationId, null, options)
        }
    }
}
