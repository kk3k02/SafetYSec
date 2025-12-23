package pt.a2025121082.isec.safetysec.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt DI module that provides Firebase singleton instances.
 *
 * These providers ensure that FirebaseAuth / Firestore / FirebaseStorage
 * are created once and can be injected anywhere in the app.
 */
@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    /** Provides a singleton FirebaseAuth instance. */
    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    /** Provides a singleton FirebaseFirestore instance. */
    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    /** Provides a singleton FirebaseStorage instance. */
    @Provides
    @Singleton
    fun provideFirebaseStorage(): FirebaseStorage = FirebaseStorage.getInstance()
}