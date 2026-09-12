plugins {
    id("com.android.application") version "9.0.1" apply false
}

// AGP 9 adds Kotlin stdlib even for Java-only modules. Reuse the locally cached
// compatible stdlib during the offline-first bootstrap build.
subprojects {
    configurations.all {
        resolutionStrategy.force("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
    }
}
