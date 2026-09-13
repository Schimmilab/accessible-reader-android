package de.schimmilab.accessiblereader

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Can the app tell WLAN from mobile data, and does it need more than the state permission to do so? */
@RunWith(AndroidJUnit4::class)
class NetKindTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun theAppCanTellWhatKindOfNetworkItIsOn() {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val caps = manager?.getNetworkCapabilities(manager.activeNetwork)
        val wifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val cellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        val unmetered = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true
        val validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        Log.i("ReaderNetKind", "Netz vorhanden: ${caps != null}, WLAN: $wifi, Mobilfunk: $cellular, " +
            "ungetaktet: $unmetered, geprüft: $validated")
        assertNotNull("Without ACCESS_NETWORK_STATE this comes back null", caps)
    }
}
