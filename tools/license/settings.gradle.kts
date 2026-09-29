// Stand-alone vendor tool (not part of the app build). Run from the repository root:
//   ./gradlew -p tools/license run --args="keygen"
pluginManagement { repositories { gradlePluginPortal(); mavenCentral() } }
dependencyResolutionManagement { repositories { mavenCentral() } }
rootProject.name = "salon-license-tool"
