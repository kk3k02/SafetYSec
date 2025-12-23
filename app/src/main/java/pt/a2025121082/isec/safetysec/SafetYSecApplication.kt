package pt.a2025121082.isec.safetysec

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class for SafetYSec.
 *
 * The @HiltAndroidApp annotation triggers Hilt's code generation and sets up
 * the application-level dependency injection container.
 *
 * This class must be registered in the AndroidManifest.xml as the application name.
 */
@HiltAndroidApp
class SafetYSecApplication : Application()