package com.jawad.takie

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.*

@SuppressLint("MissingPermission", "ClickableViewAccessibility")
class MainActivity : Activity() {

    private var walkie: Walkie? = null
    private lateinit var status: TextView
    private lateinit var deviceList: ListView
    private lateinit var hostBtn: Button
    private lateinit var talkBtn: Button
    private lateinit var picker: LinearLayout
    private var devices: List<BluetoothDevice> = emptyList()

    private val green = Color.parseColor("#1B8F3A")
    private val dark = Color.parseColor("#0E1A12")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        if (hasPermissions()) setup() else requestPermissions(neededPermissions(), 1)
    }

    private fun neededPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= 31)
            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.RECORD_AUDIO)

    private fun hasPermissions() = neededPermissions().all {
        checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(code: Int, p: Array<out String>, r: IntArray) {
        super.onRequestPermissionsResult(code, p, r)
        if (hasPermissions()) setup() else status.text = "Permission lagbe (Mic + Bluetooth). Settings theke din."
    }

    private fun setup() {
        val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter == null || !adapter.isEnabled) {
            status.text = "Age Bluetooth chalu korun, tarpor app abar kholun."
            return
        }
        walkie = Walkie(adapter,
            onStatus = { runOnUiThread { status.text = it } },
            onConnected = { ok -> runOnUiThread {
                talkBtn.visibility = if (ok) View.VISIBLE else View.GONE
                picker.visibility = if (ok) View.GONE else View.VISIBLE
            } })
        devices = adapter.bondedDevices.toList()
        deviceList.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1,
            devices.map { it.name ?: it.address })
        deviceList.setOnItemClickListener { _, _, i, _ -> walkie?.connect(devices[i]) }
        hostBtn.setOnClickListener { walkie?.listen() }
        status.text = if (devices.isEmpty())
            "Age Bluetooth settings e duita phone pair korun."
        else "Ekta phone e 'Wait' chapun, onno phone e list theke oi phone select korun."
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(dark)
            setPadding(48, 96, 48, 48)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        root.addView(TextView(this).apply {
            text = "Jawad Takie"; textSize = 34f; setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "Powered by Jawad Chowdhury"; textSize = 13f
            setTextColor(Color.parseColor("#7FD99A")); gravity = Gravity.CENTER
            setPadding(0, 8, 0, 32)
        })
        status = TextView(this).apply {
            textSize = 15f; setTextColor(Color.LTGRAY); gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        root.addView(status)

        picker = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        hostBtn = Button(this).apply { text = "Wait for connection (Host)" }
        picker.addView(hostBtn)
        picker.addView(TextView(this).apply {
            text = "Paired device (tap kore connect):"; setTextColor(Color.WHITE)
            setPadding(0, 32, 0, 8)
        })
        deviceList = ListView(this).apply { setBackgroundColor(Color.WHITE) }
        picker.addView(deviceList, LinearLayout.LayoutParams(-1, 600))
        root.addView(picker)

        talkBtn = Button(this).apply {
            text = "HOLD TO TALK"; textSize = 20f; setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(green) }
            visibility = View.GONE
            setOnTouchListener { v, e ->
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        (v.background as GradientDrawable).setColor(Color.parseColor("#E53935"))
                        text = "TALKING..."; walkie?.setTalking(true)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        (v.background as GradientDrawable).setColor(green)
                        text = "HOLD TO TALK"; walkie?.setTalking(false)
                    }
                }
                true
            }
        }
        root.addView(talkBtn, LinearLayout.LayoutParams(620, 620).apply {
            topMargin = 60; gravity = Gravity.CENTER_HORIZONTAL
        })
        setContentView(root)
    }

    override fun onDestroy() {
        walkie?.disconnect()
        super.onDestroy()
    }
}
