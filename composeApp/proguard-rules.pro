# Keep Bytedeco and JavaCV from being stripped, as they rely heavily on JNI and reflection
-keep class org.bytedeco.** { *; }

# Keep Ktor engines if dynamically loaded
-keep class io.ktor.client.engine.** { *; }

# Keep your main class
-keep class com.jpage4500.reddittv.MainKt { *; }

# Required on JVM for JNA-based integrations.
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }

# Required when using FileKit Dialogs on Linux (XDG Desktop Portal / DBus).
-keep class org.freedesktop.dbus.** { *; }
-keep class io.github.vinceglb.filekit.dialogs.platform.xdg.** { *; }
-keepattributes Signature,InnerClasses,RuntimeVisibleAnnotations