package com.example.get2class

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import android.Manifest
import android.location.Geocoder
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale
import kotlin.math.*



class ClassInfoActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ClassInfoActivity"
    }

    // for accessing the current location
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val LOCATION_PERMISSION_REQUEST_CODE = 666
    private var current_latitude: Double? = null
    private var current_longitude: Double? = null
    private var current_time: String? = null
    private var class_latitude: Double? = null
    private var class_longitude: Double? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_class_info)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val course: Course? = intent.getParcelableExtra("course")

        findViewById<TextView>(R.id.course_name).text = "${course?.name}"
        findViewById<TextView>(R.id.course_format).text = "${course?.format}"
        findViewById<TextView>(R.id.course_time).text = "${course?.startTime?.to12HourTime(false)} - ${course?.endTime?.to12HourTime(true)}"
        var days = ""
        var first = true
        if (course?.days?.get(0) == true) {
            days += "Mon"
            first = false
        }
        if (course?.days?.get(1) == true) {
            if (first) {
                days += "Tue"
                first = false
            } else {
                days += ", Tue"
            }
        }
        if (course?.days?.get(2) == true) {
            if (first) {
                days += "Wed"
                first = false
            } else {
                days += ", Wed"
            }
        }
        if (course?.days?.get(3) == true) {
            if (first) {
                days += "Thu"
                first = false
            } else {
                days += ", Thu"
            }
        }
        if (course?.days?.get(4) == true) {
            if (first) {
                days += "Fri"
            } else {
                days += ", Fri"
            }
        }
        findViewById<TextView>(R.id.course_days).text = days

        findViewById<TextView>(R.id.course_location).text = "Location: ${course?.location}"
        findViewById<TextView>(R.id.course_credits).text = "Credits: ${course?.credits}"

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Route to class Button
        findViewById<Button>(R.id.route_button).setOnClickListener {
            Log.d(TAG, "Route to class button clicked")

            Log.d(TAG, "Building: ${course?.location?.split("-")?.get(0)?.trim()}")
            val intent = Intent(this, RouteActivity::class.java)
            intent.putExtra("building", course?.location?.split("-")?.get(0)?.trim())
            startActivity(intent)
        }

        // Check attendance Button
        findViewById<Button>(R.id.check_attendance_button).setOnClickListener {
            Log.d(TAG, "Check attendance button clicked")

            val clientDate = getCurrentTime()?.split(" ") // day of week, hour, minute
            val clientDay = clientDate?.get(0)?.toInt()
            val clientTime = clientDate?.get(1)?.toDouble()?.plus(clientDate[2].toDouble()/60)
            val classStartTime = (course?.startTime?.second?.toDouble()?.div(60))?.let { it1 ->
                course.startTime.first.toDouble().plus(
                    it1
                )
            }
            var classEndTime = (course?.endTime?.second?.toDouble()?.div(60))?.let { it1 ->
                course.endTime.first.toDouble().plus(
                    it1
                )
            }
            classEndTime = classEndTime?.minus(10.0/60.0)

            // Perform null checking
            if (clientDay == null || clientTime == null || classStartTime == null || classEndTime == null) {
                Toast.makeText(
                    this,
                    "Could not get date data",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            Log.d(TAG, "Start: $classStartTime, end: $classEndTime, client: $clientTime")


            //TODO check if the course is this term

            // Check if the course is today
            if (clientDay < 1 || clientDay > 5 || !course.days[clientDay - 1]) {
                Toast.makeText(
                    this,
                    "You don't have this class today",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            // Check if the course has been attended yet
            if (course.attended) {
                Toast.makeText(this, "You already checked into this class today!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Check if it's too early
            if (clientTime < classStartTime - 10.0/60.0) {
                Toast.makeText(this, "You are too early to check into this class!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Check if it's too late
            if (classEndTime < clientTime) {
                Toast.makeText(this, "You missed your class!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val clientLocation = requestCurrentLocation()
                val classLocation = getClassLocation("UBC " + course.location.split("-")[0].trim())

                if (clientLocation.first == null || clientLocation.second == null || classLocation.first == null || classLocation.second == null) {
                    Toast.makeText(this@ClassInfoActivity, "Location data not available", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                if (coordinatesToDistance(clientLocation, classLocation) < 75) {
                    Toast.makeText(this@ClassInfoActivity, "You're too far from your class!", Toast.LENGTH_SHORT).show()
                    return@launch
                }




            }
        }

    }

    private fun getClassLocation(classAddress: String): Pair<Double?, Double?> {
        val geocoder = Geocoder(this, Locale.getDefault())
        var addresses = geocoder.getFromLocationName(classAddress, 1)
        if (!addresses.isNullOrEmpty()) {
            val location = addresses[0]
            class_latitude = location.latitude
            class_longitude = location.longitude
            Log.d(
                TAG,
                "getClassLocation: class location ($classAddress) is : ($class_latitude, $class_longitude)"
            )
            return class_latitude to class_longitude
        } else {
            // if no address found, set class to ubc book store
            addresses = geocoder.getFromLocationName("UBC Bookstore", 1)
            val location = addresses?.get(0)
            class_latitude = location?.latitude
            class_longitude = location?.longitude
            Log.d(
                TAG,
                "getClassLocation: class address not found"
            )
            Log.d(
                TAG,
                "getClassLocation: using UBC Bookstore : ($class_latitude, $class_longitude)"
            )
            return class_latitude to class_longitude
        }
    }

    private suspend fun requestCurrentLocation(): Pair<Double?, Double?> {
        return if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            == PackageManager.PERMISSION_GRANTED
        ) {
            getLastLocation()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
            Log.d(TAG, "requestCurrentLocation: Permission requested, returning null until granted")
            Pair(null, null) // Cannot proceed until user grants permission
        }
    }

    private suspend fun getLastLocation(): Pair<Double?, Double?> {
        return if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                val location = fusedLocationClient.lastLocation.await()
                if (location != null) {
                    val latitude = location.latitude
                    val longitude = location.longitude
                    Log.d(TAG, "getLastLocation: lastLocation is ($latitude, $longitude)")
                    Pair(latitude, longitude)
                } else {
                    Log.d(TAG, "getLastLocation: lastLocation is null")
                    Pair(null, null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "getLastLocation: Failed to get location", e)
                Pair(null, null)
            }
        } else {
            Log.d(TAG, "getLastLocation: Permission denied")
            Pair(null, null)
        }
    }

    private fun getCurrentTime(): String? {
        val currentTime = LocalDateTime.now()
        val dayOfWeek = currentTime.dayOfWeek.value // 1 = Monday, ..., 7 = Sunday
        val formatter = DateTimeFormatter.ofPattern("HH mm")
        current_time = "$dayOfWeek ${currentTime.format(formatter)}"

        Log.d(TAG, "getCurrentTime: $current_time")
        return current_time
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults) // Keep this at the beginning

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            lifecycleScope.launch {
                val location = getLastLocation()
                Log.d(TAG, "onRequestPermissionsResult: Location received: $location")
            }
        } else {
            Log.d(TAG, "onRequestPermissionsResult: Permission denied")
        }
    }

    fun Pair<Int, Int>.to12HourTime(end: Boolean): String {
        var (hour, minute) = this
        if (end) {
            if (minute == 30) minute = 20
            else {
                minute = 50
                hour--
            }
        }
        val amPm = if (hour < 12) "AM" else "PM"
        val hour12 = when (hour % 12) {
            0 -> 12  // 12-hour format should show 12 instead of 0 for AM/PM
            else -> hour % 12
        }
        return String.format("%d:%02d %s", hour12, minute, amPm)
    }

    fun coordinatesToDistance(coord1: Pair<Double?, Double?>, coord2: Pair<Double?, Double?>): Double {
        val R = 6378.137 // Radius of Earth in km
        val lat1 = coord1.first
        val lon1 = coord1.second
        val lat2 = coord2.first
        val lon2 = coord2.second

        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) {
            return -1.0
        }

        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        val distance = R * c * 1000 // Convert km to meters

        return distance
    }
}