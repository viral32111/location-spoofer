package com.viral32111.spoof

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
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

				// Persistent values
				sharedPreferences.edit {
					this.putFloat(sharedPreferencesLatitudeKey, latitude.toFloat())
					this.putFloat(sharedPreferencesLongitudeKey, longitude.toFloat())
					this.commit()
				}

				showOngoingNotification()

				Log.i(logTag, "Positioning map at [ $latitude, $longitude ]...")
				map.getMapAsync {
					it.moveCamera(
						CameraUpdateFactory.newLatLngZoom(
							LatLng(latitude, longitude),
							18F
						)
					)
				}

				applyButton.text = getText(R.string.toggle_button_label_stop)
				applyButton.setBackgroundColor(getColor(R.color.button_stop))
			} else {
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

		// Request permission & begin updating the map
		beginLocationUpdates()
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
	}

	private fun beginLocationUpdates() {
		// Check the location service
		val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
		if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
			Log.w(logTag, "GPS provider is disabled!")
			showMaterialDialog(R.string.dialog_message_provider_disabled)
		}
		if (!locationManager.isLocationEnabled) {
			Log.w(logTag, "Location is is disabled!")
			showMaterialDialog(R.string.dialog_message_location_disabled)
			return
		}

		// Ensure we have permission
		if (
			checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
			checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
		) {
			Log.i(logTag, "Requesting location permissions...")
			requestPermissions(arrayOf(
				Manifest.permission.ACCESS_COARSE_LOCATION,
				Manifest.permission.ACCESS_FINE_LOCATION,
			),locationPermissionRequestCode)
			return
		}

		// Periodically update the map
		val map = findViewById<MapView>(R.id.googleMap)
		locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10000L, 10F) { location ->
			val coordinates = LatLng(location.latitude, location.longitude)
			Log.i(logTag, "Positioning map at [ ${coordinates.latitude}, ${coordinates.longitude} ]...")

			map.getMapAsync { map ->
				map.isMyLocationEnabled = true

				/*
				map.addMarker(
					MarkerOptions()
						.position(coordinates)
						.title("Device Location")
						.snippet("Last updated ${SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.UK).format(Date())}")
						.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN))
				)
				*/

				map.moveCamera(CameraUpdateFactory.newLatLngZoom(coordinates, 18F))
			}
		}
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
	}

	override fun onStop() {
		super.onStop()

		val map = findViewById<MapView>(R.id.googleMap)
		map.onStop()
	}

	override fun onPause() {
		super.onPause()

		val map = findViewById<MapView>(R.id.googleMap)
		map.onPause()
	}

	override fun onResume() {
		super.onResume()

		val map = findViewById<MapView>(R.id.googleMap)
		map.onResume()
	}

	override fun onDestroy() {
		super.onDestroy()

		val map = findViewById<MapView>(R.id.googleMap)
		map.onDestroy()
	}

	override fun onSaveInstanceState(outState: Bundle) {
		super.onSaveInstanceState(outState)

		val map = findViewById<MapView>(R.id.googleMap)
		map.onSaveInstanceState(outState)
	}
}
