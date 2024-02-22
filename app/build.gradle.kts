plugins {
	id("com.android.application")
	id("org.jetbrains.kotlin.android")
}

val storeFilePath = System.getenv("ANDROID_SIGNING_STORE_PATH") ?: ""

android {
	namespace = "com.viral32111.spoof"
	compileSdk = 34

	buildToolsVersion = "34.0.0"
	ndkVersion = "26.1.10909125"

	defaultConfig {
		applicationId = "com.viral32111.spoof"

		minSdk = 29
		targetSdk = 34

		versionCode = 1 // Increment this with each release
		versionName = "0.1.0"

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
				storePassword = System.getenv("ANDROID_SIGNING_PASSWORD") ?: ""
				keyAlias = System.getenv("ANDROID_SIGNING_ALIAS") ?: ""
				keyPassword = System.getenv("ANDROID_SIGNING_PASSWORD") ?: ""
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

	// Material UI
	implementation("com.google.android.material:material:1.11.0")
	implementation("androidx.constraintlayout:constraintlayout:2.1.4")

	// Testing
	testImplementation("junit:junit:4.13.2")
	androidTestImplementation("androidx.test.ext:junit:1.1.5")
	androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
