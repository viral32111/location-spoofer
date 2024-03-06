package com.viral32111.spoof

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters

// https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running

class UpdateMockLocationWorker(
	private val context: Context,
	parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {
	companion object {
		const val LOG_TAG = "UpdateMockLocationWorker"

		const val LATITUDE_KEY = "latitude"
		const val LONGITUDE_KEY = "longitude"

		const val NOTIFICATION_CHANNEL = "ongoing"
		const val NOTIFICATION_IDENTIFIER = 800813
	}

	override suspend fun doWork(): Result {
		val latitude = inputData.getDouble(LATITUDE_KEY, -1337.0)
		val longitude = inputData.getDouble(LONGITUDE_KEY, -1337.0)
		if (latitude == -1337.0 || longitude == -1337.0) return Result.failure()

		updateOngoingNotification()
		setMockLocation(latitude, longitude)

		return Result.success()
	}

	// https://github.com/mcastillof/FakeTraveler/blob/master/app/src/main/java/cl/coders/faketraveler/MockLocationProvider.java
	private fun setMockLocation(latitude: Double, longitude: Double) {
		val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

		// Ensure location is enabled
		if (!locationManager.isLocationEnabled) {
			Log.w(LOG_TAG, "Location is is disabled!")
			return
		}

		// Ensure providers exist
		if (Build.VERSION.SDK_INT >= 31) {
			if (!locationManager.hasProvider(LocationManager.NETWORK_PROVIDER)) {
				Log.w(LOG_TAG, "Network provider is disabled!")
				return
			}
			if (!locationManager.hasProvider(LocationManager.GPS_PROVIDER)) {
				Log.w(LOG_TAG, "GPS provider is disabled!")
				return
			}
		}

		// Ensure we have permissions
		if (
			context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
			context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
		) {
			Log.w(LOG_TAG, "No permissions for coarse and/or fine location!")
			return
		}

		try {
			locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, Location(LocationManager.GPS_PROVIDER).apply {
				this.latitude = latitude
				this.longitude = longitude

				altitude = 3.0
				time = System.currentTimeMillis()
				speed = 0.01F
				bearing = 1F
				accuracy = 3F

				bearingAccuracyDegrees = 0.1F
				verticalAccuracyMeters = 0.1F
				speedAccuracyMetersPerSecond = 0.1F

				elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
			})

			locationManager.setTestProviderLocation(LocationManager.NETWORK_PROVIDER, Location(LocationManager.NETWORK_PROVIDER).apply {
				this.latitude = latitude
				this.longitude = longitude

				altitude = 3.0
				time = System.currentTimeMillis()
				speed = 0.01F
				bearing = 1F
				accuracy = 3F

				bearingAccuracyDegrees = 0.1F
				verticalAccuracyMeters = 0.1F
				speedAccuracyMetersPerSecond = 0.1F

				elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
			})

			Log.i(LOG_TAG, "Updated mock location to [ $latitude, $longitude ].")
		} catch (exception: SecurityException) {
			Log.w(LOG_TAG, "We're not set as the mock location app!")
		}
	}

	private suspend fun updateOngoingNotification() {
		val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

		// Ensure we have permission (only for Android 13)
		if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
			Log.w(LOG_TAG, "No permissions for notifications!")
			return
		}

		// Ensure notifications are enabled
		if (!notificationManager.areNotificationsEnabled()) {
			Log.w(LOG_TAG, "Notifications are disabled!")
			return
		}

		// Ensure the channel exists
		/*
		if (!notificationManager.notificationChannels.any { notificationChannel -> notificationChannel.id === NOTIFICATION_CHANNEL }) {
			Log.w(LOG_TAG, "Notification channel does not exist!")
			return
		}
		*/

		val notification = Notification.Builder(context, NOTIFICATION_CHANNEL)
			.setSmallIcon(R.drawable.auto_fix_high_24)
			.setContentTitle(context.getText(R.string.notification_ongoing_title))
			.setContentText(context.getText(R.string.notification_ongoing_text))
			.setTicker(context.getText(R.string.notification_ongoing_title))
			.setColor(context.getColor(R.color.icon_primary))
			.setAutoCancel(false) // Do not remove on press
			.setOngoing(true) // Prevents swiping away
			.build()

		notificationManager.notify(NOTIFICATION_IDENTIFIER, notification)
		setForeground(ForegroundInfo(NOTIFICATION_IDENTIFIER, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION))

		Log.i(LOG_TAG, "Updated on-going notification.")
	}
}
