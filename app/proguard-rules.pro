# Glance widgets & ActionCallbacks (instantiated by reflection from RemoteViews intents)
-keep class com.lokesh.tidy.widget.** { *; }
-keep class * implements androidx.glance.appwidget.action.ActionCallback { *; }
-keep class androidx.glance.** { *; }

# BroadcastReceivers & Room database / entities
-keep class com.lokesh.tidy.reminder.** { *; }
-keep class com.lokesh.tidy.data.TidyDatabase_Impl { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }
