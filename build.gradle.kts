// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
	id("com.android.application") version "8.2.2" apply false
	id("org.jetbrains.kotlin.android") version "1.9.22" apply false
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
