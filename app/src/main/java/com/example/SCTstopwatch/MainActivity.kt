package com.example.SCTstopwatch

import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.MediaStore
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.apache.poi.xssf.usermodel.XSSFWorkbook


class MainActivity : AppCompatActivity() {

    private lateinit var timerTextView: TextView
    private lateinit var stopResetButton: Button
    private lateinit var lapResumeButton: Button
    private lateinit var uploadButton: Button
    private lateinit var lapTimesListView: ListView
    private val handler = Handler(Looper.getMainLooper())
    private var startTime: Long = 0
    private var timeInMilliseconds: Long = 0
    private var isRunning = false
    private val lapTimes = ArrayList<String>()
    private lateinit var adapter: LapTimesAdapter
    private lateinit var enableBluetoothLauncher: ActivityResultLauncher<Intent>

    companion object {
        private const val REQUEST_PERMISSIONS = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        enableBluetoothLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        }

        timerTextView = findViewById(R.id.timer)
        stopResetButton = findViewById(R.id.stop_reset_button)
        lapResumeButton = findViewById(R.id.lap_resume_button)
        uploadButton = findViewById(R.id.upload_button)
        lapTimesListView = findViewById(R.id.lap_times)

        adapter = LapTimesAdapter(this, lapTimes)
        lapTimesListView.adapter = adapter

        stopResetButton.visibility = View.GONE
        "Start".also { lapResumeButton.text = it }
        "00:00.0".also { timerTextView.text = it }

        lapResumeButton.setOnClickListener {
            if (isRunning) {
                recordLap()
            } else {
                startTimer()
            }
        }

        stopResetButton.setOnClickListener {
            if (isRunning) {
                stopTimer()
            } else {
                resetTimer()
            }
        }

        uploadButton.setOnClickListener { uploadResults() }

        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        val permissions = arrayOf(
            android.Manifest.permission.BLUETOOTH,
            android.Manifest.permission.BLUETOOTH_ADMIN,
            android.Manifest.permission.BLUETOOTH_CONNECT,
            android.Manifest.permission.BLUETOOTH_SCAN
        )

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), REQUEST_PERMISSIONS)
        } else {
            // Permissions are already granted, proceed with Bluetooth operations
            enableBluetooth()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                // All permissions are granted, proceed with Bluetooth operations
                enableBluetooth()
            } else {
                // Permissions are denied, show a message to the user
                Toast.makeText(this, "Permissions are required for Bluetooth operations", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun enableBluetooth() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
            ?: // Device doesn't support Bluetooth
            return
        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableBtIntent)
        }
    }

    private fun startTimer() {
        startTime = System.currentTimeMillis()
        handler.postDelayed(updateTimerThread, 0)
        isRunning = true
        "Stop".also { stopResetButton.text = it }
        stopResetButton.visibility = View.VISIBLE
        "Lap".also { lapResumeButton.text = it }
        val v = getSystemService(VIBRATOR_SERVICE) as Vibrator
        v.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun stopTimer() {
        timeInMilliseconds += System.currentTimeMillis() - startTime
        handler.removeCallbacks(updateTimerThread)
        isRunning = false
        "Reset".also { stopResetButton.text = it }
        "Resume".also { lapResumeButton.text = it }
        uploadButton.isEnabled = true
    }

    private fun resetTimer() {
        timeInMilliseconds = 0L
        "00:00.0".also { timerTextView.text = it }
        lapTimes.clear()
        adapter.notifyDataSetChanged()
        stopResetButton.visibility = View.GONE
        "Start".also { lapResumeButton.text = it }
        uploadButton.isEnabled = false
    }

    private fun recordLap() {
        val lapTime = System.currentTimeMillis() - startTime + timeInMilliseconds
        lapTimes.add("Lap ${lapTimes.size + 1} | ${formatTime(lapTime)}")
        adapter.notifyDataSetChanged()
        val v = getSystemService(VIBRATOR_SERVICE) as Vibrator
        v.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
    }


    private val updateTimerThread: Runnable = object : Runnable {
        override fun run() {
            val updatedTime = System.currentTimeMillis() - startTime + timeInMilliseconds
            timerTextView.text = formatTime(updatedTime)
            handler.postDelayed(this, 0)
        }
    }
    private fun formatTime(time: Long): String {
        val millis = (time % 1000) / 100
        val secs = (time / 1000).toInt()
        val mins = secs / 60
        val seconds = secs % 60
        val minutes = mins % 60
        return String.format(getString(R.string._02d_02d_01d), minutes, seconds, millis)
    }
    private fun showDistanceDialog(onDistanceSelected: (String) -> Unit) {
        val distances = arrayOf("750m", "1250m", "2000m", "2500m", "Custom")
        val builder = AlertDialog.Builder(this)
        val view = layoutInflater.inflate(R.layout.dialog_distance, null)
        val spinner: Spinner = view.findViewById(R.id.distance_spinner)
        val customDistanceEditText: EditText = view.findViewById(R.id.custom_distance_edit_text)

        customDistanceEditText.visibility = View.GONE

        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, distances)

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                customDistanceEditText.visibility = if (distances[position] == "Custom") View.VISIBLE else View.GONE
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                customDistanceEditText.visibility = View.GONE
            }
        }

        builder.setView(view)
            .setTitle("Select Race Distance")
            .setPositiveButton("OK") { dialog, _ ->
                val selectedDistance = if (spinner.selectedItem == "Custom") {
                    customDistanceEditText.text.toString()
                } else {
                    spinner.selectedItem.toString()
                }
                if (selectedDistance.isNotBlank()) {
                    onDistanceSelected(selectedDistance)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }

        builder.create().show()
    }


    private fun uploadResults() {
        showDistanceDialog { selectedDistance ->
            val date = java.text.SimpleDateFormat("ddMMM", java.util.Locale.getDefault()).format(java.util.Date())
            val fileName = "${selectedDistance}-${date}.xlsx"

            val resolver = contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }

            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

            uri?.let {
                resolver.openOutputStream(it)?.use { outputStream ->
                    val workbook = XSSFWorkbook()
                    val sheet = workbook.createSheet("Lap Times")
                    for ((index, lap) in lapTimes.withIndex()) {
                        val row = sheet.createRow(index)
                        val lapData = lap.split("| ")
                        val cell1 = row.createCell(0)
                        cell1.setCellValue(lapData[0])
                        val cell2 = row.createCell(1)
                        cell2.setCellValue(lapData[1])
                    }
                    workbook.write(outputStream)
                    workbook.close()
                }

                // New code to open the Android share menu
                val shareIntent: Intent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_STREAM, uri)
                    type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, "Share File"))
            }
        }
    }


}
