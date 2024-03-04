plugins {
	id("com.android.application")
	id("org.jetbrains.kotlin.android")

	// Google Maps
	id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")

	// Firebase
	id("com.google.gms.google-services")
	id("com.google.firebase.crashlytics")
	id("com.google.firebase.firebase-perf")
}

val storeFilePath = System.getenv("ANDROID_KEY_STORE_PATH") ?: ""

android {
	namespace = "com.viral32111.spoof"
	compileSdk = 34

	buildToolsVersion = "34.0.0"
	ndkVersion = "26.1.10909125"

	defaultConfig {
		applicationId = "com.viral32111.spoof"

		minSdk = 29
		targetSdk = 34

		versionCode = 3 // Increment this with each release
		versionName = "0.3.0"

		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

		signingConfig = signingConfigs.getByName("debug")
		vectorDrawables {
			useSupportLibrary = true
		}
	}

	signingConfigs {
		// Use CI environment variables for signing releases
		create("release") {
			if (storeFilePath.isNotBlank()) {
				storeFile = file(storeFilePath)
				storePassword = System.getenv("ANDROID_KEY_STORE_PASSWORD") ?: ""
				keyAlias = System.getenv("ANDROID_KEY_STORE_KEY_ALIAS") ?: ""
				keyPassword = System.getenv("ANDROID_KEY_STORE_KEY_PASSWORD") ?: ""
			}
		}
	}

	buildTypes {
		getByName("debug") {
			isDebuggable = true // Force debugging
		}

		release {
			// Prevent debugging
			isDebuggable = false

			// Optimise size
			isMinifyEnabled = true
			isShrinkResources = true
			proguardFiles(
				getDefaultProguardFile("proguard-android-optimize.txt"),
				"proguard-rules.pro"
			)

			// Sign only if the environment variables are present
			if (storeFilePath.isNotBlank()) {
				signingConfig = signingConfigs.getByName("release")
			}
		}
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_1_8
		targetCompatibility = JavaVersion.VERSION_1_8
	}

	kotlinOptions {
		jvmTarget = "1.8"
	}

	packaging {
		resources {
			excludes += "/META-INF/{AL2.0,LGPL2.1}"
		}
	}
}

dependencies {
	// Core
	implementation("androidx.core:core-ktx:1.12.0")
	implementation("androidx.appcompat:appcompat:1.6.1")
	implementation("androidx.constraintlayout:constraintlayout:2.1.4")

	// Material UI
	implementation("com.google.android.material:material:1.11.0")

	// Google Maps
	implementation("com.google.android.gms:play-services-maps:18.2.0")

	// Splash Screen - https://developer.android.com/develop/ui/views/launch/splash-screen
	implementation("androidx.core:core-splashscreen:1.0.1")

	// Firebase - https://firebase.google.com/docs/android/setup#available-libraries
	implementation(platform("com.google.firebase:firebase-bom:32.7.3"))
	implementation("com.google.firebase:firebase-analytics")
	implementation("com.google.firebase:firebase-crashlytics")
	implementation("com.google.firebase:firebase-perf")

	// Play Integrity API
	implementation("com.google.android.play:integrity:1.3.0")

	// Testing
	testImplementation("junit:junit:4.13.2")
	androidTestImplementation("androidx.test.ext:junit:1.1.5")
	androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
