package pt.a2025121082.isec.safetysec

import android.app.Application
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import dagger.hilt.android.HiltAndroidApp
import pt.a2025121082.isec.safetysec.BuildConfig

/**
 * Application class for SafetYSec.
 *
 * The @HiltAndroidApp annotation triggers Hilt's code generation and sets up
 * the application-level dependency injection container.
 *
 * This class must be registered in the AndroidManifest.xml as the application name.
 */
@HiltAndroidApp
class SafetYSecApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val appCheck = FirebaseAppCheck.getInstance()
        if (BuildConfig.DEBUG) {
            appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
        } else {
            appCheck.installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
        }
    }
}
