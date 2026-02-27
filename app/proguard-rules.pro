# ============================================
# PROGUARD RULES - OPTIMIZADO PARA REDMI NOTE 14 4G
# ============================================

# Optimizaciones agresivas
-optimizationpasses 5
-allowaccessmodification
-repackageclasses ''

# Room Database - Necesario para que funcione
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

# Coil (carga de imágenes)
-keep class coil.** { *; }
-dontwarn coil.**

# Compose - Reglas necesarias
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Kotlin Coroutines
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# Eliminar logs en release
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# No mostrar warnings de librerías
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**