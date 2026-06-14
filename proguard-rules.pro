





-keep class juloo.keyboard2.passwordmanager.** { *; }
-keep class juloo.keyboard2.passwordmanager.ui.** { *; }

-keepattributes Signature
-keepattributes *Annotation*

-keep class androidx.room.RoomDatabase { *; }
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Google Drive API Client fixes
-dontwarn javax.naming.**
-dontwarn javax.naming.directory.**
-dontwarn javax.naming.ldap.**
-dontwarn org.ietf.jgss.**
-dontwarn org.apache.http.**
-dontwarn org.apache.commons.logging.**

# Google API Client JSON mapping
-keep class com.google.api.client.** { *; }
-keep class com.google.api.services.drive.** { *; }
