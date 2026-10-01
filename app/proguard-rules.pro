# Salon Manager release shrinking rules.
# Room, Compose, Lifecycle and Navigation ship their own consumer rules.

# Keep line numbers so crash stack traces from customers can be mapped with mapping.txt.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Enums are persisted by name in the database and in backups: never rename them.
-keepclassmembers enum com.dtpos.salonmanager.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    <fields>;
}

# Credential Manager (Google sign-in) loads its Play Services provider by reflection.
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** { *; }
