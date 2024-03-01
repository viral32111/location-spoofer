// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
	id("com.android.application") version "8.3.0" apply false
	id("org.jetbrains.kotlin.android") version "1.9.22" apply false

	// Firebase
	id("com.google.gms.google-services") version "4.4.1" apply false
	id("com.google.firebase.crashlytics") version "2.9.9" apply false
	id("com.google.firebase.firebase-perf") version "1.4.2" apply false
}

// Google Maps
buildscript {
	dependencies {
		classpath("com.google.android.libraries.mapsplatform.secrets-gradle-plugin:secrets-gradle-plugin:2.0.1")
	}
}

// Accept ToS for Gradle build scanning
if (hasProperty("buildScan")) {
	extensions.findByName("buildScan")?.withGroovyBuilder {
		setProperty("termsOfServiceUrl", "https://gradle.com/terms-of-service")
		setProperty("termsOfServiceAgree", "yes")
	}
}
