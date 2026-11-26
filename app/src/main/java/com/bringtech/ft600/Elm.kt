package com.bringtech.ft600
import android.annotation.SuppressLint
import android.bluetooth.*
import java.io.*
import java.util.UUID

@SuppressLint("MissingPermission")
class Elm {
    private var sock: BluetoothSocket? = null
    private var inp: InputStream? = null
    private var out: OutputStream? = null

    fun connect(d: BluetoothDevice): Boolean = try {
        BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery()
        val s = d.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
        s.connect(); sock = s; inp = s.inputStream; out = s.outputStream
        for (c in listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATSP0")) cmd(c, 4000)
        true
    } catch (e: Exception) { false }

    @Synchronized
    fun cmd(c: String, timeout: Long = 3000): String {
        out!!.write((c + "\r").toByteArray()); out!!.flush()
        val sb = StringBuilder(); val end = System.currentTimeMillis() + timeout
        while (System.currentTimeMillis() < end) {
            if (inp!!.available() > 0) { val ch = inp!!.read().toChar(); if (ch == '>') break; sb.append(ch) }
            else Thread.sleep(10)
        }
        return sb.toString().replace("\r", " ").trim()
    }

    fun pid(p: String): List<Int>? {
        val r = cmd(p).replace(" ", "")
        val i = r.indexOf("41" + p.substring(2)); if (i < 0) return null
        return r.substring(i + 4).chunked(2).mapNotNull { it.toIntOrNull(16) }
    }

    fun volt(): Float? = cmd("ATRV").replace("V", "").trim().toFloatOrNull()

    fun close() { try { sock?.close() } catch (_: Exception) {} }
}
