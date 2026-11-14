# R8 rules for the android app. NOTE: composeApp/proguard-rules.pro is the *desktop* ProGuard
# config (JNA, dbus, bytedeco) and is deliberately not referenced here.

# keep our own class/member names. -keepnames is "-keep,allowshrinking", so unused code is still
# removed — it only blocks renaming, which keeps the stack traces LogManager uploads readable
# without needing mapping.txt on the server side.
-keepnames class com.jpage4500.** { *; }

# slf4j binds its provider through ServiceLoader
-keep class org.slf4j.simple.** { *; }

# ktor server (ReceiverManager): its IntelliJ debugger detector reaches for java.lang.management,
# which Android doesn't have. it's only ever called under a JVM debug agent
-dontwarn java.lang.management.**
