# Manifest-referenced Android components
-keep class com.whitepoint.reducer.MainActivity { *; }
-keep class com.whitepoint.reducer.ShortcutActionActivity { *; }
-keep class com.whitepoint.reducer.WhitePointTileService { *; }
-keep class com.whitepoint.reducer.ScheduleReceiver { *; }
-keep class com.whitepoint.reducer.WhitePointAutomationReceiver { *; }
-keep class com.whitepoint.reducer.WhitePointWidgetReceiver { *; }

# Jetpack Glance AppWidget & ActionCallbacks instantiated via reflection
-keep class com.whitepoint.reducer.WhitePointGlanceWidget { *; }
-keep class * implements androidx.glance.appwidget.action.ActionCallback {
    public <init>();
    *;
}
-keepclassmembers class * extends androidx.glance.appwidget.GlanceAppWidget {
    *;
}

# Kyant0 Liquid Glass (Backdrop) & Shapes (AGSL RuntimeShader / Compose Modifier.Node)
-keep class com.kyant.backdrop.** { *; }
-keep class com.kyant.shapes.** { *; }
-dontwarn com.kyant.backdrop.**
-dontwarn com.kyant.shapes.**

# Baseline Profile Installer
-keep class androidx.profileinstaller.** { *; }

# Strip debug/verbose logging in R8 release builds
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
