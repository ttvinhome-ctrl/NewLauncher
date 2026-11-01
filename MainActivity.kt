package id.kebasen.launcher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme

class MainActivity : ComponentActivity() {
    private lateinit var st: St
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        st = St(this)
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        setContent { MaterialTheme { HomeScreen(st) } }
    }
    override fun onResume() { super.onResume(); st.apps = loadApps(this) }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); st.library = false; st.panel = "" }
}
