package com.viral32111.spoof

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.location.LocationRequest
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import com.google.firebase.analytics.logEvent
import kotlin.math.log

class MainActivity : AppCompatActivity() {
	private val logTag = "MainActivity"

	private val sharedPreferencesKey = "fakeCoordinates"
	private val sharedPreferencesLatitudeKey = "latitude"
	private val sharedPreferencesLongitudeKey = "longitude"

	private val locationPermissionRequestCode = 42069
	private val notificationPermissionRequestCode = 69420

	private val ongoingNotificationChannel = "ongoing"
	private val ongoingNotificationIdentifier = 800813

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)

		val map = findViewById<MapView>(R.id.googleMap)
		map.onCreate(savedInstanceState)
		map.getMapAsync {
			// Use the well known Google maps style
			it.mapType = GoogleMap.MAP_TYPE_NORMAL

			// Disable traffic & indoor views
			it.isBuildingsEnabled = true
			it.isIndoorEnabled = false
			it.isTrafficEnabled = false

			// Default to London
			it.moveCamera( CameraUpdateFactory.newLatLngZoom(LatLng(51.50722, -0.1275), 15F))
			Log.i(logTag, "Positioned map in London.")
		}

		// Get buttons
		val latitudeInput = findViewById<EditText>(R.id.latitudeInput)
		val longitudeInput = findViewById<EditText>(R.id.longitudeInput)
		val applyButton = findViewById<Button>(R.id.toggleButton)
		val clearButton = findViewById<Button>(R.id.clearButton)

		// Get keyboard & notification managers
		val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

		// Retrieve persistent values
		val sharedPreferences = getSharedPreferences(sharedPreferencesKey, Context.MODE_PRIVATE)
		val persistentLatitude = sharedPreferences.getFloat(sharedPreferencesLatitudeKey, -1337F)
		val persistentLongitude = sharedPreferences.getFloat(sharedPreferencesLongitudeKey, -1337F)

		// Update UI if persistent values exist
		if (persistentLatitude != -1337F) latitudeInput.setText(persistentLatitude.toString())
		if (persistentLongitude != -1337F) longitudeInput.setText(persistentLatitude.toString())

		// Change the map position when the apply button is pressed
		applyButton.setOnClickListener {
			val isStartButton = applyButton.text.toString() == getText(R.string.toggle_button_label_start)

			if (isStartButton) {
				val latitude = latitudeInput.text.toString().toDoubleOrNull()
				val longitude = longitudeInput.text.toString().toDoubleOrNull()

				if (
					(latitude == null || latitude.isNaN()) ||
					(longitude == null || longitude.isNaN())
				) {
					Log.w(logTag, "Invalid values for latitude and/or longitude input(s)!")
					showMaterialDialog(R.string.dialog_message_inputs, false)
					return@setOnClickListener
				}

				// Hide keyboard
				latitudeInput.clearFocus()
				longitudeInput.clearFocus()
				if (currentFocus?.windowToken != null)
					inputMethodManager.hideSoftInputFromWindow(currentFocus!!.windowToken, 0)

				// Persist values
				sharedPreferences.edit {
					this.putFloat(sharedPreferencesLatitudeKey, latitude.toFloat())
					this.putFloat(sharedPreferencesLongitudeKey, longitude.toFloat())
					this.commit()
				}

				// Fake the location :)
				setupMockLocation()
				setMockLocation(LocationManager.GPS_PROVIDER, latitude, longitude)
				setMockLocation(LocationManager.NETWORK_PROVIDER, latitude, longitude)

				// Let them know that we're doing stuff
				showOngoingNotification()

				// Visualise changes
				Log.i(logTag, "Positioning map at [ $latitude, $longitude ]...")
				map.getMapAsync {
					it.moveCamera(
						CameraUpdateFactory.newLatLngZoom(
							LatLng(latitude, longitude),
							18F
						)
					)
				}

				// Swap button
				applyButton.text = getText(R.string.toggle_button_label_stop)
				applyButton.setBackgroundColor(getColor(R.color.button_stop))
			} else {
				// No more magic
				teardownMockLocation()

				// We've stopped doing stuff
				hideOngoingNotification()

				// Swap button
				applyButton.text = getText(R.string.toggle_button_label_start)
				applyButton.setBackgroundColor(getColor(R.color.button_start))
			}
		}

		// Reset input values when the clear button is pressed
		clearButton.setOnClickListener {
			latitudeInput.text.clear()
			longitudeInput.text.clear()

			// Hide keyboard
			latitudeInput.clearFocus()
			longitudeInput.clearFocus()
			if (currentFocus?.windowToken != null)
				inputMethodManager.hideSoftInputFromWindow(currentFocus!!.windowToken, 0)

			// Clear persistent values
			sharedPreferences.edit {
				this.clear()
				this.commit()
			}
		}

		// Log opening the app via Analytics
		Firebase.analytics.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT) {
			param(FirebaseAnalytics.Param.CONTENT_TYPE, "activity")
			param(FirebaseAnalytics.Param.CONTENT, "MainActivity")
		}
	}

	private fun getLocationManager(): LocationManager? {
		val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

		// Ensure location is enabled
		if (!locationManager.isLocationEnabled) {
			Log.w(logTag, "Location is is disabled!")
			showMaterialDialog(R.string.dialog_message_location_disabled)
			return null
		}

		// Ensure providers exist
		if (Build.VERSION.SDK_INT >= 31) {
			if (!locationManager.hasProvider(LocationManager.NETWORK_PROVIDER)) {
				Log.w(logTag, "Network provider is disabled!")
				showMaterialDialog(R.string.dialog_message_provider_network_disabled)
				return null
			}
			if (!locationManager.hasProvider(LocationManager.GPS_PROVIDER)) {
				Log.w(logTag, "GPS provider is disabled!")
				showMaterialDialog(R.string.dialog_message_provider_gps_disabled)
				return null
			}
		}

		// Ensure we have permissions
		if (
			checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
			checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
		) {
			Log.i(logTag, "Requesting location permissions...")
			requestPermissions(arrayOf(
				Manifest.permission.ACCESS_COARSE_LOCATION,
				Manifest.permission.ACCESS_FINE_LOCATION,
			),locationPermissionRequestCode)
			return null
		}

		return locationManager
	}

	@SuppressLint("WrongConstant")
	private fun setupMockLocation() {
		val locationManager = getLocationManager() ?: return

		try {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
				locationManager.addTestProvider(
					LocationManager.GPS_PROVIDER,
					false,
					false,
					false,
					false,
					false,
					true,
					true,
					ProviderProperties.POWER_USAGE_LOW,
					ProviderProperties.ACCURACY_FINE
				)

				locationManager.addTestProvider(
					LocationManager.NETWORK_PROVIDER,
					false,
					false,
					false,
					false,
					false,
					true,
					true,
					ProviderProperties.POWER_USAGE_LOW,
					ProviderProperties.ACCURACY_FINE
				)
			} else {
				locationManager.addTestProvider(
					LocationManager.GPS_PROVIDER,
					false,
					false,
					false,
					false,
					false,
					true,
					true,
					0,
					0
				)

				locationManager.addTestProvider(
					LocationManager.NETWORK_PROVIDER,
					false,
					false,
					false,
					false,
					false,
					true,
					true,
					0,
					0
				)
			}

			Log.i(logTag, "Added mock location providers.")
		} catch (exception: SecurityException) {
			Log.w(logTag, "We're not set as the mock location app!")
			showMaterialDialog(R.string.dialog_message_no_mock_location)
		}
	}

	private fun teardownMockLocation() {
		val locationManager = getLocationManager() ?: return

		locationManager.removeTestProvider(LocationManager.GPS_PROVIDER)
		locationManager.removeTestProvider(LocationManager.NETWORK_PROVIDER)

		Log.i(logTag, "Removed mock location providers.")
	}

	// https://github.com/mcastillof/FakeTraveler/blob/master/app/src/main/java/cl/coders/faketraveler/MockLocationProvider.java
	private fun setMockLocation(provider: String, latitude: Double, longitude: Double) {
		val locationManager = getLocationManager() ?: return

		// Either LocationManager.NETWORK_PROVIDER or LocationManager.GPS_PROVIDER
		val fakeLocation = Location(provider).apply {
			this.latitude = latitude
			this.longitude = longitude

			this.altitude = 3.0
			this.time = System.currentTimeMillis()
			this.speed = 0.01F
			this.bearing = 1F
			this.accuracy = 3F
		}

		fakeLocation.bearingAccuracyDegrees = 0.1F
		fakeLocation.verticalAccuracyMeters = 0.1F
		fakeLocation.speedAccuracyMetersPerSecond = 0.1F

		fakeLocation.elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()

		locationManager.setTestProviderLocation(provider, fakeLocation)

		Log.i(logTag, "Updated mock location to [ $latitude, $longitude ].")
	}

	private fun showMaterialDialog(@StringRes message: Int, isFinisher: Boolean = true) {
		val dialogBuilder = MaterialAlertDialogBuilder(this)
			.setTitle(R.string.dialog_title)
			.setMessage(message)

		if (isFinisher)
			dialogBuilder.setNegativeButton(R.string.dialog_negative_button) { _, _ ->
				finish()
			}.setOnDismissListener {
				finish()
			}.setOnCancelListener {
				finish()
			}
		else
			dialogBuilder.setPositiveButton(R.string.dialog_positive_button) { _, _ -> }

		dialogBuilder.show()
	}

	private fun showOngoingNotification() {
		val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

		// Ensure we have permission (only for Android 13)
		if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
			Log.i(logTag, "Requesting notification permissions...")
			requestPermissions(
				arrayOf(Manifest.permission.POST_NOTIFICATIONS),
				notificationPermissionRequestCode)
			return
		}
		if (!notificationManager.areNotificationsEnabled()) {
			Log.w(logTag, "Notifications are disabled!")
			showMaterialDialog(R.string.dialog_message_notifications_disabled)
			return
		}

		notificationManager.createNotificationChannel(
			NotificationChannel(
				ongoingNotificationChannel,
				getText(R.string.notification_ongoing_channel_name),
				NotificationManager.IMPORTANCE_LOW
			).apply {
				description = getText(R.string.notification_ongoing_channel_description).toString()
			}
		)

		val notification = Notification.Builder(this, ongoingNotificationChannel)
			.setSmallIcon(R.drawable.auto_fix_high_24)
			.setContentTitle(getText(R.string.notification_ongoing_title))
			.setContentText(getText(R.string.notification_ongoing_text))
			.setColor(getColor(R.color.icon_primary))
			.setAutoCancel(false) // Do not remove on press
			.setOngoing(true) // Prevents swiping away
			.build()

		notificationManager.notify(ongoingNotificationIdentifier, notification)
		Log.i(logTag, "Shown on-going notification.")
	}

	private fun hideOngoingNotification() {
		val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

		// Ensure we have permission (only for Android 13)
		if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
			Log.i(logTag, "Requesting notification permissions...")
			requestPermissions(
				arrayOf(Manifest.permission.POST_NOTIFICATIONS),
				notificationPermissionRequestCode)
			return
		}
		if (!notificationManager.areNotificationsEnabled()) {
			Log.w(logTag, "Notifications are disabled!")
			showMaterialDialog(R.string.dialog_message_notifications_disabled)
			return
		}

		notificationManager.cancel(ongoingNotificationIdentifier)
		Log.i(logTag, "Hidden on-going notification.")
	}

	private fun beginLocationUpdates() {
		val map = findViewById<MapView>(R.id.googleMap)
		val locationManager = getLocationManager() ?: return

		// It's stupid that we have to repeat this
		if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return

		// Instantly update the map with last known location
		map.getMapAsync {
			it.isMyLocationEnabled = true

			val lastKnown = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
			if (lastKnown == null) {
				Log.w(logTag, "No last known GPS provider location!")
				return@getMapAsync
			}
			val coordinates = LatLng(lastKnown.latitude, lastKnown.longitude)

			it.moveCamera(CameraUpdateFactory.newLatLngZoom(coordinates, 18F))
		}

		// Periodically update the map with real time
		locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10000L, 5F) { location ->
			val coordinates = LatLng(location.latitude, location.longitude)
			Log.i(logTag, "Positioning map at GPS provider location [ ${coordinates.latitude}, ${coordinates.longitude} ]...")

			map.getMapAsync { map ->
				map.isMyLocationEnabled = true

				map.moveCamera(CameraUpdateFactory.newLatLngZoom(coordinates, 18F))
			}
		}

		Log.i(logTag, "Began periodic location updates.")
	}

	override fun onRequestPermissionsResult(
		requestCode: Int,
		permissions: Array<out String>,
		grantResults: IntArray
	) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults)

		val isPermissionsGranted = permissions.all {
			grantResults[permissions.indexOf(it)] == PackageManager.PERMISSION_GRANTED
		}

		if (requestCode == locationPermissionRequestCode) {
			if (!isPermissionsGranted) {
				Log.w(logTag, "Not all location permissions were granted!")
				showMaterialDialog(R.string.dialog_message_grant_permission_location)
				return
			}

			beginLocationUpdates()
		}

		if (requestCode == notificationPermissionRequestCode) {
			if (!isPermissionsGranted) {
				Log.w(logTag, "Not all notification permissions were granted!")
				showMaterialDialog(R.string.dialog_message_grant_permission_notification)
				return
			}

			showOngoingNotification()
		}
	}

	override fun onStart() {
		super.onStart()

		val map = findViewById<MapView>(R.id.googleMap)
		map.onStart()

		Log.i(logTag, "Activity started.")

		// Request permission & begin updating the map
		beginLocationUpdates()
	}

	override fun onStop() {
		super.onStop()

		val map = findViewById<MapView>(R.id.googleMap)
		map.onStop()

		Log.i(logTag, "Activity stopped.")

		hideOngoingNotification()
		teardownMockLocation()
	}

	override fun onPause() {
		super.onPause()

		val map = findViewById<MapView>(R.id.googleMap)
		map.onPause()

		Log.i(logTag, "Activity paused.")
	}

	override fun onResume() {
		super.onResume()

		val map = findViewById<MapView>(R.id.googleMap)
		map.onResume()

		Log.i(logTag, "Activity resumed.")
	}

	override fun onDestroy() {
		super.onDestroy()

		val map = findViewById<MapView>(R.id.googleMap)
		map.onDestroy()

		Log.i(logTag, "Activity destroyed.")

		hideOngoingNotification()
		teardownMockLocation()
	}

	override fun onSaveInstanceState(outState: Bundle) {
		super.onSaveInstanceState(outState)

		val map = findViewById<MapView>(R.id.googleMap)
		map.onSaveInstanceState(outState)

		Log.i(logTag, "Activity instance saved.")
	}
}
