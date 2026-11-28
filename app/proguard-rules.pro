# Notely release rules.
#
# Room: generated implementations are looked up reflectively by name, and the
# entity/DAO classes are serialised through the generated code, so keep them.
-keep class com.notely.app.data.db.** { *; }
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**

# App entry points referenced only from the manifest.
-keep class com.notely.app.MainActivity { *; }
-keep class com.notely.app.reminder.ReminderReceiver { *; }
-keep class com.notely.app.reminder.BootReceiver { *; }

# FileProvider is instantiated by the framework from the manifest.
-keep class androidx.core.content.FileProvider { *; }

# ML Kit text recognition is loaded via its own reflection-based component
# registration; the optional OCR path must not crash the build when minified.
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# kotlinx.serialization / JSON: Notely parses document JSON with org.json only,
# but keep the metadata so Kotlin reflection on data classes remains safe.
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-dontwarn org.json.**

# Coroutines
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
