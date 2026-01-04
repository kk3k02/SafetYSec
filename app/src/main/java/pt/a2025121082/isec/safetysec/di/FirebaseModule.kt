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
 * Dagger Hilt module responsible for providing Firebase singleton instances.
 * 
 * These providers allow for easy dependency injection of Firebase services 
 * across the application, ensuring that only one instance of each service exists.
 */
@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    /**
     * Provides a singleton instance of [FirebaseAuth].
     * Used for user authentication, login, and registration.
     */
    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    /**
     * Provides a singleton instance of [FirebaseFirestore].
     * Used for storing and retrieving user profiles, rules, and alert data.
     */
    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    /**
     * Provides a singleton instance of [FirebaseStorage].
     * Used for uploading and retrieving evidence videos during emergency alerts.
     */
    @Provides
    @Singleton
    fun provideFirebaseStorage(): FirebaseStorage = FirebaseStorage.getInstance()
}
