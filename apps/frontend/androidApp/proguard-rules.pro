# =============================================================================
#  Reguły R8/ProGuard dla wersji release (isMinifyEnabled = true).
#  Bez nich logowanie Google działa w debug, a w podpisanym release pada,
#  bo R8 usuwa/obfuskuje klasy odpytywane refleksyjnie przez Credential Manager.
# =============================================================================

# --- Credential Manager (androidx.credentials) ---
# Provider Play Services jest ładowany przez ServiceLoader / refleksję — musi przetrwać.
-keep class androidx.credentials.** { *; }
-keep interface androidx.credentials.** { *; }
-keep class androidx.credentials.playservices.** { *; }
-keep class * extends androidx.credentials.provider.CredentialProviderService { *; }

# --- Sign in with Google (googleid) ---
# GetGoogleIdOption / GoogleIdTokenCredential.createFrom(...) używane w GoogleSignInHelper.
-keep class com.google.android.libraries.identity.googleid.** { *; }

# --- Google Play Services auth / identity ---
-keep class com.google.android.gms.auth.api.identity.** { *; }
-keep class com.google.android.gms.common.api.** { *; }

# --- kotlinx.serialization ---
# Generowane serializery są odwoływane refleksyjnie; bez keepów API (ktor + JSON)
# przestaje działać w release. Trzymamy serializery i adnotacje.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class pl.rkarpinski.fiszkiwbiegu.**$$serializer { *; }
-keepclassmembers class pl.rkarpinski.fiszkiwbiegu.** {
    *** Companion;
}
