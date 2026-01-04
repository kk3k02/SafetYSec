package pt.a2025121082.isec.safetysec

import android.app.Application
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import dagger.hilt.android.HiltAndroidApp
import pt.a2025121082.isec.safetysec.BuildConfig

/**
 * Main Application class for SafetYSec.
 *
 * The @HiltAndroidApp annotation triggers Hilt's code generation, which includes 
 * a base class for your application that serves as the application-level 
 * dependency injection container.
 *
 * This class must be registered in the AndroidManifest.xml under the <application> tag.
 */
@HiltAndroidApp
class SafetYSecApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        
        // Initialize Firebase App Check to protect API resources from unauthorized access
        initializeFirebaseAppCheck()
    }

    /**
     * Configures Firebase App Check based on the build type.
     */
    private fun initializeFirebaseAppCheck() {
        val appCheck = FirebaseAppCheck.getInstance()
        
        if (BuildConfig.DEBUG) {
            // Use DebugAppCheckProviderFactory in debug mode to allow testing on emulators/simulators
            appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
        } else {
            // Use PlayIntegrityAppCheckProviderFactory in production for device integrity verification
            appCheck.installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
        }
    }
}
