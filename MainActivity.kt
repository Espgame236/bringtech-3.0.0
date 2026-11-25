package com.bringtech.ft600
import android.Manifest.permission.*
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.*
import android.hardware.*
import android.content.*
import android.graphics.Color
import android.os.Bundle
import android.provider.MediaStore
import android.view.*
import android.widget.*
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.PagerTitleStrip
import androidx.viewpager.widget.ViewPager
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

class Pg(val views: List<View>, val titles: List<String>) : PagerAdapter() {
    override fun getCount() = views.size
    override fun isViewFromObject(v: View, o: Any) = v === o
    override fun instantiateItem(c: ViewGroup, i: Int): Any { c.addView(views[i]); return views[i] }
    override fun destroyItem(c: ViewGroup, i: Int, o: Any) { c.removeView(o as View) }
    override fun getPageTitle(i: Int) = titles[i]
}

@SuppressLint("MissingPermission")
class MainActivity : Activity(), SensorEventListener {
    val elm = Elm()
    @Volatile var connected = false
    @Volatile var rec: OutputStream? = null
    var t0 = 0L; var n = 0
    lateinit var status: TextView; lateinit var logInfo: TextView; lateinit var dtcTxt: TextView
    lateinit var ft: FtView; lateinit var motor: FtList; lateinit var picos: FtList

    fun btn(t: String, f: () -> Unit) = Button(this).apply { text = t; setOnClickListener { f() } }
    fun tv(t: String, s: Float = 16f) = TextView(this).apply { text = t; textSize = s; setTextColor(Color.WHITE); setPadding(24, 16, 24, 16) }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        requestPermissions(arrayOf(BLUETOOTH_CONNECT, BLUETOOTH_SCAN, ACCESS_FINE_LOCATION), 1)

        ft = FtView(this); val painel = ft
        ft.setOnLongClickListener {
            val o = listOf(4500f, 5000f, 6000f, 8000f); ft.maxRpm = o[(o.indexOf(ft.maxRpm) + 1) % o.size]
            Toast.makeText(this, "Fundo de escala ${ft.maxRpm.toInt()} rpm", Toast.LENGTH_SHORT).show(); ft.upd(); true
        }
        motor = FtList(this); picos = FtList(this, "Toque para zerar os picos"); picos.setOnClickListener { resetPeaks() }
        dtcTxt = tv("Toque em Ler falhas.")
        val falhas = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(0, 90, 0, 0)
            addView(btn("Ler falhas") { readDtc() }); addView(btn("Apagar falhas e luz MIL") { clearDtc() }); addView(dtcTxt)
        }
        logInfo = tv("Pronto. Conecte e toque em iniciar.")
        val datalog = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(0, 90, 0, 0)
            addView(btn("Iniciar gravacao") { startRec() }); addView(btn("Parar gravacao") { stopRec() }); addView(logInfo)
        }
        val pager = ViewPager(this)
        pager.adapter = Pg(listOf(painel, motor, picos, falhas, datalog), listOf("PAINEL", "MOTOR", "PICOS", "FALHAS", "GRAVAR DATALOG"))
        val strip = PagerTitleStrip(this); strip.setTextColor(0xFFFF6A00.toInt())
        pager.addView(strip, ViewPager.LayoutParams().apply { width = -1; height = -2; gravity = Gravity.TOP })

        status = tv("Desconectado", 14f)
        val top = LinearLayout(this).apply { addView(btn("Conectar") { pick() }); addView(status) }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(0xFF0B0F14.toInt())
            addView(top); addView(pager, LinearLayout.LayoutParams(-1, 0, 1f))
        })
    }

    fun pick() {
        val ad = BluetoothAdapter.getDefaultAdapter() ?: return
        val devs = ArrayList<BluetoothDevice>(); val names = ArrayList<String>()
        val la = ArrayAdapter(this, android.R.layout.simple_list_item_1, names)
        fun add(d: BluetoothDevice) { if (devs.none { it.address == d.address }) { devs.add(d); names.add("${d.name ?: "?"}\n${d.address}"); la.notifyDataSetChanged() } }
        ad.bondedDevices.forEach { add(it) }
        val rc = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                if (i.action == BluetoothDevice.ACTION_FOUND) add(i.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)!!)
            }
        }
        registerReceiver(rc, IntentFilter(BluetoothDevice.ACTION_FOUND)); ad.startDiscovery()
        val lv = ListView(this); lv.adapter = la
        val dlg = AlertDialog.Builder(this).setTitle("Dispositivos").setView(lv).setOnDismissListener {
            try { unregisterReceiver(rc) } catch (_: Exception) {}; ad.cancelDiscovery()
        }.create()
        lv.setOnItemClickListener { _, _, i, _ -> dlg.dismiss(); connect(devs[i]) }
        dlg.show()
    }

    fun ui(f: () -> Unit) = runOnUiThread { f() }

    fun connect(d: BluetoothDevice) {
        status.text = "Conectando..."
        Thread {
            val ok = elm.connect(d)
            connected = ok
            if (ok) getSharedPreferences("bt", 0).edit().putString("last", d.address).apply()
            ui { status.text = if (ok) "Conectado: ${d.name}" else "Falha ao conectar" }
            if (ok) poll()
        }.start()
    }

    val sv = HashMap<String, Float>(); val bad = HashSet<String>(); var si = 0
    var pRpm = 0f; var pBoost = 0f; var pEgt = 0f; var pWater = 0f; var pSpd = 0f
    var z0 = 0L; var best = 0f; var armed = false

    fun resetPeaks() { pRpm = 0f; pBoost = 0f; pEgt = 0f; pWater = 0f; pSpd = 0f; best = 0f; armed = false; z0 = 0L }
    fun f(x: Float?, d: Int = 0) = if (x == null) "--" else "%.${d}f".format(x)

    fun dec(p: String, d: List<Int>): Float? = try {
        when (p) {
            "0105", "010F", "015C" -> d[0] - 40f
            "0133" -> d[0].toFloat()
            "0123" -> (d[0] * 256 + d[1]) * 10f
            "0178" -> (d[1] * 256 + d[2]) / 10f - 40
            "0110" -> (d[0] * 256 + d[1]) / 100f
            "010E" -> d[0] / 2f - 64
            "012F" -> d[0] * 100f / 255
            else -> null
        }
    } catch (e: Exception) { null }

    fun poll() {
        val slow = listOf("0105", "010F", "0133", "015C", "0123", "0178", "0110", "010E", "012F")
        while (connected) {
            try {
                val r = elm.pid("010C"); val s = elm.pid("010D"); val l = elm.pid("0104"); val m = elm.pid("010B"); val a = elm.pid("0111")
                repeat(2) {
                    val p = slow[si++ % slow.size]
                    if (p !in bad) { val x = elm.pid(p)?.let { dec(p, it) }; if (x != null) sv[p] = x else bad.add(p) }
                }
                val bt = elm.volt()
                val rp = if (r != null && r.size >= 2) (r[0] * 256 + r[1]) / 4f else null
                val sp = s?.getOrNull(0)?.toFloat(); val ld = l?.getOrNull(0)?.let { it * 100f / 255 }
                val mp = m?.getOrNull(0)?.toFloat(); val th = a?.getOrNull(0)?.let { it * 100f / 255 }
                val tc = sv["0105"]; val ic = sv["010F"]; val baro = sv["0133"] ?: 100f
                val oil = sv["015C"]; val rail = sv["0123"]; val egt = sv["0178"]; val maf = sv["0110"]; val adv = sv["010E"]; val fuel = sv["012F"]
                val boost = mp?.minus(baro)
                pRpm = maxOf(pRpm, rp ?: 0f); pBoost = maxOf(pBoost, boost ?: 0f); pEgt = maxOf(pEgt, egt ?: 0f)
                pWater = maxOf(pWater, tc ?: 0f); pSpd = maxOf(pSpd, sp ?: 0f)
                val now = System.currentTimeMillis(); val spd = sp ?: 0f
                if (spd < 1f) { armed = true; z0 = 0L } else if (armed && z0 == 0L) z0 = now
                if (armed && z0 != 0L && spd >= 100f) { best = (now - z0) / 1000f; armed = false }
                ui {
                    ft.rpm = rp ?: 0f; ft.speed = spd; ft.load = ld ?: 0f; ft.map = mp ?: 0f; ft.tps = th ?: 0f
                    ft.bat = bt ?: 0f; ft.water = tc ?: 0f; ft.air = ic ?: 0f; ft.upd()
                    motor.upd(listOf(
                        Row("TURBO kPa (MAP - barometrica)", f(boost), (boost ?: 0f) / 150f, (boost ?: 0f) > 160f),
                        Row("TRILHO bar", f(rail?.div(100f)), (rail ?: 0f) / 200000f),
                        Row("TEMP ESCAPE C", f(egt), (egt ?: 0f) / 900f, (egt ?: 0f) > 750f),
                        Row("MAF g/s", f(maf, 1), (maf ?: 0f) / 300f),
                        Row("AVANCO graus", f(adv, 1), ((adv ?: 0f) + 10f) / 40f),
                        Row("OLEO C", f(oil), (oil ?: 0f) / 150f, (oil ?: 0f) > 125f),
                        Row("COMBUSTIVEL %", f(fuel), (fuel ?: 0f) / 100f)))
                    picos.upd(listOf(
                        Row("RPM MAX", f(pRpm), pRpm / ft.maxRpm), Row("TURBO MAX kPa", f(pBoost), pBoost / 150f),
                        Row("ESCAPE MAX C", f(pEgt), pEgt / 900f), Row("AGUA MAX C", f(pWater), pWater / 120f, pWater > 105f),
                        Row("VELOC MAX km/h", f(pSpd), pSpd / 200f),
                        Row("0-100 km/h (segundos)", if (best > 0f) f(best, 1) else "--", if (best > 0f) 1f - best / 20f else 0f)))
                }
                rec?.let {
                    val line = "${(now - t0) / 1000f},${rp ?: ""},${sp ?: ""},${ld ?: ""},${mp ?: ""},${th ?: ""},${tc ?: ""},${ic ?: ""},${bt ?: ""},${baro},${boost ?: ""},${oil ?: ""},${rail ?: ""},${egt ?: ""},${maf ?: ""},${adv ?: ""},${fuel ?: ""}\n"
                    it.write(line.toByteArray()); n++
                    if (n % 5 == 0) ui { logInfo.text = "Gravando... $n linhas" }
                }
            } catch (e: Exception) { connected = false; ui { status.text = "Conexao perdida" } }
        }
    }

    fun startRec() {
        if (rec != null) return
        try {
            val name = "datalog_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".csv"
            val v = ContentValues().apply { put(MediaStore.Downloads.DISPLAY_NAME, name); put(MediaStore.Downloads.MIME_TYPE, "text/csv") }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v)!!
            val os = contentResolver.openOutputStream(uri)!!
            os.write("tempo_s,rpm,velocidade_kmh,carga_pct,map_kpa,acelerador_pct,temp_agua_c,temp_ar_c,bateria_v,baro_kpa,turbo_kpa,oleo_c,trilho_kpa,escape_c,maf_gs,avanco_graus,combustivel_pct\n".toByteArray())
            t0 = System.currentTimeMillis(); n = 0; rec = os; ft.rec = true; ft.upd()
            logInfo.text = "Gravando em Downloads/$name"
        } catch (e: Exception) { logInfo.text = "Erro ao criar arquivo" }
    }

    fun stopRec() {
        val os = rec ?: return
        rec = null; try { os.close() } catch (_: Exception) {}; ft.rec = false; ft.upd()
        logInfo.text = "Parado. $n linhas salvas em Downloads (CSV)."
    }

    fun readDtc() {
        if (!connected) { dtcTxt.text = "Conecte primeiro."; return }
        Thread {
            var h = elm.cmd("03").replace(" ", "")
            val i = h.indexOf("43"); h = if (i >= 0) h.substring(i + 2) else ""
            if (h.length % 4 == 2) h = h.substring(2)
            val codes = h.chunked(4).filter { it.length == 4 && it != "0000" }.map {
                val x = it.substring(0, 1).toInt(16)
                "PCBU"[x shr 2] + (x and 3).toString() + it.substring(1)
            }
            ui { dtcTxt.text = if (codes.isEmpty()) "Nenhuma falha." else codes.joinToString("\n") }
        }.start()
    }

    fun clearDtc() {
        if (!connected) { dtcTxt.text = "Conecte primeiro."; return }
        AlertDialog.Builder(this).setTitle("Apagar falhas?").setMessage("Apaga os codigos e apaga a luz MIL.")
            .setPositiveButton("Apagar") { _, _ -> Thread { val r = elm.cmd("04"); ui { dtcTxt.text = "Resposta: $r" } }.start() }
            .setNegativeButton("Cancelar", null).show()
    }

    override fun onRequestPermissionsResult(c: Int, p: Array<out String>, r: IntArray) {
        val a = getSharedPreferences("bt", 0).getString("last", null) ?: return
        if (r.isNotEmpty() && r[0] == 0 && !connected) try { connect(BluetoothAdapter.getDefaultAdapter().getRemoteDevice(a)) } catch (_: Exception) {}
    }

    override fun onResume() {
        super.onResume(); val sm = getSystemService(SENSOR_SERVICE) as SensorManager
        sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }
    override fun onPause() { super.onPause(); (getSystemService(SENSOR_SERVICE) as SensorManager).unregisterListener(this) }
    override fun onSensorChanged(e: SensorEvent) { ft.gx = -e.values[0] / 9.8f; ft.gz = -e.values[2] / 9.8f; ft.upd() }
    override fun onAccuracyChanged(s: Sensor?, a: Int) {}

    override fun onDestroy() { connected = false; stopRec(); elm.close(); super.onDestroy() }
}
