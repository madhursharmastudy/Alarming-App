// Add this inside your app/build.gradle.kts, inside the `android { }` block.
// This reads the keystore path/passwords from environment variables (used by
// the GitHub Actions workflow) so the SAME key signs every build — which is
// required for Android to accept an update over an already-installed app.

android {
    // ... your existing config (namespace, compileSdk, defaultConfig, etc.) ...

    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("KEYSTORE_FILE") ?: "release-key.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
            keyAlias = System.getenv("KEY_ALIAS") ?: ""
            keyPassword = System.getenv("KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
