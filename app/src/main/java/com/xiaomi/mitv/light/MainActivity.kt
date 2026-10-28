package com.xiaomi.mitv.light

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.*
import android.view.*
import android.widget.*
import com.xiaomi.mitv.light.runtime.LightAccess
import com.xiaomi.mitv.light.tcl.*
import com.xiaomi.mitv.light.ui.*
import org.json.JSONObject

/** FrameLuma's centered landscape dashboard; all primary controls stay on one screen. */
class MainActivity : Activity(), UiFrameConsumer {
    private lateinit var backdrop: AmbientBackdrop
    private lateinit var preview: StripPreview
    private lateinit var power: ControlTile
    private lateinit var brightnessValue: TextView
    private lateinit var speedChoices: List<TextView>
    private lateinit var direction: ControlTile
    private lateinit var device: TextView
    private lateinit var connection: TextView
    private lateinit var status: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var ownColors: IntArray? = null
    private var pendingBrightness: Int? = null
    private val saveBrightness = Runnable {
        pendingBrightness?.let { AppCore.setBrightness(it); Client.refresh(this) }
        pendingBrightness = null
    }
    private val refresh = object : Runnable {
        override fun run() { render(); handler.postDelayed(this, 400) }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val root = FrameLayout(this)
        backdrop = AmbientBackdrop(this)
        root.addView(backdrop, FrameLayout.LayoutParams(-1, -1))
        val panel = DashboardPanel(this)
        panel.place(ImageView(this).apply { setImageResource(R.drawable.ic_launcher) }, 28, 24, 36, 36)
        panel.place(designText(this, "映光", 28f).apply { id = R.id.brand_title; typeface = Typeface.DEFAULT_BOLD }, 78, 22, 94, 40)
        panel.place(designText(this, "FRAME LUMA", 12f, 0xff9eb9b1.toInt()).apply { letterSpacing = .17f }, 181, 32, 180, 22)
        connection = designText(this, "", 12f).apply { gravity = Gravity.CENTER_VERTICAL or Gravity.END }
        panel.place(connection, 686, 30, 178, 25)
        panel.place(designText(this, "画面之外，也有光。", 20f).apply { typeface = Typeface.DEFAULT_BOLD }, 36, 100, 338, 32)
        panel.place(designText(this, "九区环绕 · 实时随影", 12f, 0xff93b4ac.toInt()), 36, 137, 338, 22)
        preview = StripPreview(this)
        panel.place(preview, 34, 179, 334, 179)
        device = designText(this, "", 12f, 0xffa1b9b2.toInt())
        panel.place(device, 36, 371, 337, 21)
        status = designText(this, "准备画面…", 11f, 0xff7f9c94.toInt())
        panel.place(status, 36, 399, 338, 18)

        power = tile(panel, R.id.light_toggle, "灯带随影", 98)
        power.setOnClickListener {
            val enabled = !AppCore.enabled()
            if (enabled) AppCore.setEnabled(true) else AppCore.setPower(false)
            Client.flow(this, enabled); render()
        }
        val brightness = tile(panel, R.id.brightness_control, "亮度", 164, 146)
        val steps = brightness.choices(listOf("−", "", "+")) { changeBrightness(if (it == 0) -5 else 5) }
        brightnessValue = steps[1]
        brightness.setOnClickListener { changeBrightness(5) }
        brightness.setOnKeyListener { _, code, event ->
            if (event.action == KeyEvent.ACTION_DOWN && code in intArrayOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)) {
                changeBrightness(if (code == KeyEvent.KEYCODE_DPAD_LEFT) -5 else 5); true
            } else false
        }
        val speed = tile(panel, R.id.speed_control, "速度", 230, 146)
        speedChoices = speed.choices(listOf("低", "中", "快")) { setSpeed(it) }
        speedChoices.forEach { it.background = ChoiceSurface() }
        speed.setOnClickListener { changeSpeed(1) }
        speed.setOnKeyListener { _, code, event ->
            if (event.action == KeyEvent.ACTION_DOWN && code in intArrayOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)) {
                changeSpeed(if (code == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1); true
            } else false
        }
        direction = tile(panel, R.id.direction_control, "环绕方向", 296)
        direction.setOnClickListener {
            guarded { AppCore.setLayout(AppCore.layout().json().put("reverse", !AppCore.layout().reverse)); Client.refresh(this) }; render()
        }
        val calibration = ControlTile(this, "布局校准", 20).apply {
            id = R.id.calibration_control; value.text = "↗"
            setOnClickListener { startActivity(Intent(this@MainActivity, CalibrationActivity::class.java)) }
        }
        val about = ControlTile(this, "关于映光", 20).apply {
            id = R.id.about_control; value.text = "↗"; setOnClickListener { showDetails() }
        }
        panel.place(calibration, 430, 366, 210, 44); panel.place(about, 654, 366, 210, 44)
        power.nextFocusDownId = brightness.id; brightness.nextFocusUpId = power.id
        brightness.nextFocusDownId = speed.id; speed.nextFocusUpId = brightness.id
        speed.nextFocusDownId = direction.id; direction.nextFocusUpId = speed.id
        direction.nextFocusDownId = calibration.id; calibration.nextFocusUpId = direction.id; about.nextFocusUpId = direction.id
        calibration.nextFocusRightId = about.id; about.nextFocusLeftId = calibration.id
        root.addView(CenteredStage(this, panel), FrameLayout.LayoutParams(-1, -1))
        setContentView(root); power.requestFocus(); render()
    }
    private fun tile(panel: DashboardPanel, viewId: Int, label: String, top: Int, valueWidth: Int = 180): ControlTile {
        val tile = ControlTile(this, label, valueWidth).apply { id = viewId }
        panel.place(tile, 430, top, 434, 56); return tile
    }
    private fun changeBrightness(delta: Int) {
        pendingBrightness = ((pendingBrightness ?: AppCore.brightness()) + delta).coerceIn(1, 100)
        handler.removeCallbacks(saveBrightness); handler.postDelayed(saveBrightness, 600); render()
    }
    private fun speedIndex() = when (AppCore.period()) { 1000 -> 0; 500 -> 1; else -> 2 }
    private fun setSpeed(index: Int) { AppCore.setPeriod(intArrayOf(1000, 500, 150)[index]); Client.refresh(this); render() }
    private fun changeSpeed(delta: Int) = setSpeed((speedIndex() + delta + 3) % 3)
    override fun onUiFrame(colors: IntArray) { ownColors = colors; updatePreview() }
    private fun updatePreview() {
        guarded {
            val layout = AppCore.layout()
            preview.update(ownColors ?: IntArray(9) { 0x34665b }, layout.geometry(), layout.reverse)
        }
    }
    private fun render() {
        power.value.text = if (AppCore.enabled()) "开启  ●" else "关闭  ○"
        power.value.setTextColor(if (AppCore.enabled()) 0xffa3f6ce.toInt() else 0xff90a8a0.toInt())
        brightnessValue.text = "${pendingBrightness ?: AppCore.brightness()}%"
        speedChoices.forEachIndexed { i, view ->
            view.isSelected = i == speedIndex()
            view.setTextColor(if (i == speedIndex()) 0xffb7ffdc.toInt() else 0xff6b8880.toInt())
            view.typeface = if (i == speedIndex()) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        guarded {
            direction.value.text = if (AppCore.layout().reverse) "底部中央 → 右" else "底部中央 → 左"
            val engine = JSONObject(AppCore.state()).optJSONObject("engine")
            val identified = engine?.optString("device_name", "") ?: ""
            device.text = if (identified.isNotBlank() && !engine?.optString("device_id", "").isNullOrBlank()) identified else "${Build.BRAND} · ${Build.MODEL}"
            val present = LightAccess.present(this)
            connection.text = if (present) "●  灯带已连接" else "○  等待连接灯带"
            connection.setTextColor(if (present) 0xffa3f6ce.toInt() else 0xffd5b58d.toInt())
            val error = engine?.optString("error", "") ?: ""
            status.text = when {
                !present -> "连接USB灯带，开始随影"
                !AppCore.enabled() -> "随影已关闭"
                error.isNotEmpty() -> error.take(70)
                ownColors != null && engine?.optBoolean("running", false) == true -> "光，正在跟随画面流动"
                else -> "准备随影画面…"
            }
        }
        updatePreview()
    }
    private fun showDetails() {
        val engine = JSONObject(AppCore.state()).optJSONObject("engine")
        val message = "FrameLuma · 映光\n\n${Build.BRAND} / ${Build.PRODUCT} / ${Build.DEVICE}\n型号：${Build.MODEL}\n\n更新间隔：${engine?.optInt("output_interval_ms", 0) ?: 0}ms\n当前取色：${if (engine?.optString("capture_source") == "own_app") "映光界面" else "显示器画面"}\n\n本项目采用Apache-2.0。设备适配、灯带协议和布局独立维护。开源组件与参考来源保留各自许可。"
        AlertDialog.Builder(this).setTitle("关于映光").setMessage(message).setPositiveButton("返回", null).setNeutralButton("开源许可") { _, _ ->
            val entries = assets.list("licenses")?.sorted()?.toTypedArray() ?: emptyArray()
            AlertDialog.Builder(this).setTitle("许可与来源").setItems(entries) { _, i ->
                val content = assets.open("licenses/${entries[i]}").bufferedReader().use { it.readText() }
                AlertDialog.Builder(this).setTitle(entries[i]).setMessage(content).setPositiveButton("返回", null).show()
            }.show()
        }.show()
    }
    private fun guarded(action: () -> Unit) { try { action() } catch (error: Exception) { if (::status.isInitialized) status.text = error.message ?: "配置不可用" } }
    override fun onResume() { super.onResume(); backdrop.active = true; handler.post(refresh); Client.send(this, "STATUS"); if (AppCore.enabled()) Client.flow(this, true) }
    override fun onPause() { handler.removeCallbacks(refresh); handler.removeCallbacks(saveBrightness); saveBrightness.run(); backdrop.active = false; super.onPause() }
}
