package com.jawad.takie

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import java.io.IOException
import java.util.UUID

@SuppressLint("MissingPermission")
class Walkie(
    private val adapter: BluetoothAdapter,
    private val onStatus: (String) -> Unit,
    private val onConnected: (Boolean) -> Unit
) {
    companion object {
        val APP_UUID: UUID = UUID.fromString("8f1d2c3a-5b6e-4a7f-9c0d-1e2f3a4b5c6d")
        const val RATE = 8000
    }

    private var server: BluetoothServerSocket? = null
    private var socket: BluetoothSocket? = null
    @Volatile private var talking = false
    @Volatile private var running = false

    fun setTalking(on: Boolean) { talking = on }

    fun listen() {
        disconnect()
        onStatus("Onno phone er connection er jonno wait korchi...")
        Thread {
            try {
                val s = adapter.listenUsingRfcommWithServiceRecord("JawadTakie", APP_UUID)
                server = s
                val client = s.accept()
                s.close()
                start(client)
            } catch (e: Exception) {
                onStatus("Wait error: ${e.message}")
            }
        }.start()
    }

    fun connect(device: BluetoothDevice) {
        disconnect()
        onStatus("Connect korchi: ${device.name ?: device.address}...")
        Thread {
            try {
                val s = device.createRfcommSocketToServiceRecord(APP_UUID)
                s.connect()
                start(s)
            } catch (e: Exception) {
                onStatus("Connect hoy nai: ${e.message}")
            }
        }.start()
    }

    private fun start(s: BluetoothSocket) {
        socket = s
        running = true
        onStatus("Connected: ${s.remoteDevice.name ?: s.remoteDevice.address}")
        onConnected(true)
        Thread { receiveLoop(s) }.start()
        Thread { sendLoop(s) }.start()
    }

    private fun receiveLoop(s: BluetoothSocket) {
        val minBuf = AudioTrack.getMinBufferSize(
            RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val track = AudioTrack(
            AudioManager.STREAM_MUSIC, RATE, AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT, minBuf * 2, AudioTrack.MODE_STREAM
        )
        track.play()
        val buf = ByteArray(1024)
        try {
            val input = s.inputStream
            while (running) {
                val n = input.read(buf)
                if (n < 0) break
                track.write(buf, 0, n)
            }
        } catch (_: IOException) {
        } finally {
            track.stop(); track.release()
            lost()
        }
    }

    private fun sendLoop(s: BluetoothSocket) {
        val minBuf = AudioRecord.getMinBufferSize(
            RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val rec = AudioRecord(
            MediaRecorder.AudioSource.MIC, RATE, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, minBuf * 2
        )
        val buf = ByteArray(1024)
        var recording = false
        try {
            val out = s.outputStream
            while (running) {
                if (talking) {
                    if (!recording) { rec.startRecording(); recording = true }
                    val n = rec.read(buf, 0, buf.size)
                    if (n > 0) out.write(buf, 0, n)
                } else {
                    if (recording) { rec.stop(); recording = false }
                    Thread.sleep(20)
                }
            }
        } catch (_: Exception) {
        } finally {
            try { if (recording) rec.stop() } catch (_: Exception) {}
            rec.release()
            lost()
        }
    }

    private fun lost() {
        if (running) {
            running = false
            onStatus("Connection bichchhinno hoye geche")
            onConnected(false)
        }
        try { socket?.close() } catch (_: IOException) {}
    }

    fun disconnect() {
        running = false
        talking = false
        try { server?.close() } catch (_: IOException) {}
        try { socket?.close() } catch (_: IOException) {}
        server = null
        socket = null
    }
}
