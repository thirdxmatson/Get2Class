package com.example.get2class

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Parcelize
data class Course(
    val name: String,
    val days: List<Boolean>,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val location: String,
    val credits: Double
) : Parcelable

@Parcelize
data class Schedule(val courses: List<Course>) : Parcelable

class UploadSchedule : AppCompatActivity() {

    private lateinit var schedule: Schedule

    companion object {
        private const val REQUEST_CODE = 100 // Define request code for file selection
        private const val TAG = "UploadSchedule"
        private const val LISTING = 1
        private const val CREDITS = 2
        private const val MODE = 6
        private const val PATTERN = 7
        private const val START_DATE = 10
        private const val END_DATE = 11
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_schedule_upload)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Launch the file picker to select an .xlsx file
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        }
        startActivityForResult(intent, REQUEST_CODE)
    }

    // Handle the result from the file picker
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                schedule = readExcelFromUri(uri)
                val resultIntent = Intent().apply {
                    putExtra("schedule", schedule)
                }
                setResult(RESULT_OK, resultIntent) // Send result back to MainActivity
                finish() // Ensure the activity finishes after setting the result
            }
        } else {
            setResult(RESULT_CANCELED) // Handle case where user cancels selection
            finish()
        }
    }

    // Step 3: Read the Excel file and process its data
    private fun readExcelFromUri(uri: Uri): Schedule {
        val courses: MutableList<Course> = mutableListOf()
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val workbook = WorkbookFactory.create(inputStream)
                val sheet = workbook.getSheetAt(0)

                var rowNum = 0
                for (row in sheet) {
                    // Ignore classes that aren't in person
                    if (row.getCell(MODE).toString() != "In Person Learning") {
                        rowNum++
                        continue
                    }

                    if (rowNum > 2) {
                        // Create the full name value
                        val listing = row.getCell(LISTING).toString()
                        val fullName = listing.substringBefore('_') + ' ' + listing.substringAfter(' ')
                        Log.d(TAG, "Full name: $fullName")

                        // Get the credits
                        val credits = row.getCell(CREDITS).toString().toDouble()
                        Log.d(TAG, "Credits: $credits")

                        // Create the start date and end date
                        val dateFormatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy")
                        val startDate = LocalDate.parse(row.getCell(START_DATE).toString(), dateFormatter)
                        Log.d(TAG, "Start Date: $startDate")
                        val endDate = LocalDate.parse(row.getCell(END_DATE).toString(), dateFormatter)
                        Log.d(TAG, "End Date: $endDate")

                        // Get the meeting pattern
                        val pattern = row.getCell(PATTERN).toString()
                        if (pattern != "") {
                            val patternList = pattern.split("|")

                            // Create the days list
                            val days = patternList[1].split(" ")
                            val daysBool = mutableListOf(false, false, false, false, false)
                            if (days.contains("Mon")) daysBool[0] = true
                            if (days.contains("Tue")) daysBool[1] = true
                            if (days.contains("Wed")) daysBool[2] = true
                            if (days.contains("Thu")) daysBool[3] = true
                            if (days.contains("Fri")) daysBool[4] = true
                            Log.d(TAG, "Days: $daysBool")

                            // Create the start time
                            val times = patternList[2].split("-")
                            val timeFormatter = DateTimeFormatter.ofPattern(" h:mm a ", Locale.ENGLISH)
                            val startTime = LocalTime.parse(times[0].replace(".", "").uppercase(), timeFormatter)
                            Log.d(TAG, "Start time: $startTime")

                            // Create the end time
                            val endTime = LocalTime.parse(times[1].replace(".", "").uppercase(), timeFormatter).minusMinutes(10)
                            Log.d(TAG, "End time: $endTime")

                            // Get the building code
                            val building = patternList[3].substringBefore("-").trim()
                            Log.d(TAG, "Building: $building")

                            // Make the course object
                            courses.add(Course(fullName, daysBool, startTime, endTime, startDate, endDate, building, credits))
                        }

                    }
                    Log.d(TAG, "---------------------------------------------------------------------------")
                    rowNum++
                }
                Log.d(TAG, "Courses object: $courses")

                workbook.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return Schedule(courses)
    }
}