plugins {
    kotlin("jvm") version "2.0.21"
    application
}



// Re-uses the exact licence codec that ships in the app, so keys always match.
val appSources = rootDir.resolve("../../app/src/main/java")
sourceSets {
    main {
        kotlin.srcDir(appSources)
        kotlin.include("com/dtpos/salonmanager/domain/license/**", "LicenseTool.kt")
    }
}

application { mainClass.set("LicenseToolKt") }

tasks.named<JavaExec>("run") { workingDir = projectDir }
