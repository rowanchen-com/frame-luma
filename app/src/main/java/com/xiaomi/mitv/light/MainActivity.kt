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
    private lateinit var powerSwitch: SlideToggle
    private var renderingSwitch = false
    private lateinit var brightnessSlider: BrightnessSlider
    private lateinit var speedChoices: List<TextView>
    private lateinit var direction: ControlTile
    private lateinit var device: TextView
    private lateinit var connection: TextView
    private lateinit var controllerName: TextView
    private lateinit var controllerModel: TextView
    private lateinit var status: TextView
    private lateinit var latency: TextView
    private lateinit var sweepLatency: TextView
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
        preview = StripPreview(this).apply { id = R.id.color_preview }
        panel.place(preview, 470, 40, 400, 240)
        panel.place(designText(this, "FrameLuma", 13f).apply { id = R.id.brand_title; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) }, 26, 344, 116, 22)
        connection = designText(this, "", 10f).apply { id = R.id.connection_status }
        panel.place(connection, 26, 376, 116, 17)
        panel.place(designText(this, "灯光设备", 10f, UiTheme.muted).apply { id = R.id.light_group_label }, 178, 333, 274, 15)
        panel.place(designText(this, "显示设备", 10f, UiTheme.muted).apply { id = R.id.display_group_label }, 494, 333, 188, 15)
        panel.place(designText(this, "同步速度", 10f, UiTheme.muted).apply { id = R.id.sync_group_label; gravity = Gravity.CENTER_VERTICAL or Gravity.END }, 726, 333, 148, 15)
        controllerName = designText(this, "", 12f, UiTheme.secondary).apply { id = R.id.controller_name }
        controllerModel = designText(this, "", 10f, UiTheme.muted).apply { id = R.id.controller_model }
        panel.place(controllerName, 178, 354, 274, 20); panel.place(controllerModel, 178, 378, 274, 15)
        device = designText(this, "", 11f, UiTheme.secondary).apply { id = R.id.display_info }
        panel.place(device, 494, 354, 188, 20)
        status = designText(this, "准备画面…", 10f, UiTheme.muted).apply { id = R.id.source_status }
        panel.place(status, 494, 378, 188, 15)
        latency = designText(this, "", 12f).apply { id = R.id.sync_latency; gravity = Gravity.CENTER_VERTICAL or Gravity.END }
        sweepLatency = designText(this, "", 10f, UiTheme.muted).apply { id = R.id.sweep_latency; gravity = Gravity.CENTER_VERTICAL or Gravity.END }
        panel.place(latency, 726, 354, 148, 20); panel.place(sweepLatency, 726, 378, 148, 15)

        power = tile(panel, R.id.light_toggle, "灯带随影", 24, 80)
        powerSwitch = SlideToggle(this).apply { id = R.id.light_switch }
        power.values.removeAllViews(); power.values.addView(powerSwitch, LinearLayout.LayoutParams(-2, -1))
        powerSwitch.setOnCheckedChangeListener { _, checked -> if (!renderingSwitch) applyEnabled(checked) }
        power.setOnClickListener { powerSwitch.performClick() }
        power.setOnKeyListener { _, code, event ->
            if (event.action == KeyEvent.ACTION_DOWN && code in intArrayOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)) {
                applyEnabled(code == KeyEvent.KEYCODE_DPAD_RIGHT); true
            } else false
        }
        val brightness = tile(panel, R.id.brightness_control, "亮度", 82, 235)
        brightnessSlider = BrightnessSlider(this) { stageBrightness(it) }
        brightness.values.removeAllViews(); brightness.values.addView(brightnessSlider, LinearLayout.LayoutParams(-1, -1))
        brightness.setOnKeyListener { _, code, event ->
            if (event.action == KeyEvent.ACTION_DOWN && code in intArrayOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)) {
                changeBrightness(if (code == KeyEvent.KEYCODE_DPAD_LEFT) -5 else 5); true
            } else false
        }
        val speed = tile(panel, R.id.speed_control, "速度", 140, 146)
        speedChoices = speed.choices(listOf("低", "中", "快")) { setSpeed(it) }
        speed.values.background = SegmentTrack()
        speedChoices.forEach { it.background = ChoiceSurface() }
        speed.setOnClickListener { changeSpeed(1) }
        speed.setOnKeyListener { _, code, event ->
            if (event.action == KeyEvent.ACTION_DOWN && code in intArrayOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)) {
                changeSpeed(if (code == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1); true
            } else false
        }
        direction = tile(panel, R.id.direction_control, "环绕方向", 198)
        direction.setOnClickListener {
            guarded { AppCore.setLayout(AppCore.layout().json().put("reverse", !AppCore.layout().reverse)); Client.refresh(this) }; render()
        }
        val calibration = ControlTile(this, "布局校准", 20).apply {
            id = R.id.calibration_control; value.text = "↗"
            setOnClickListener { startActivity(Intent(this@MainActivity, CalibrationActivity::class.java)) }
        }
        val about = ControlTile(this, "关于", 20).apply {
            id = R.id.about_control; value.text = "↗"; setOnClickListener { showDetails() }
        }
        panel.place(calibration, 24, 266, 192, 40); panel.place(about, 232, 266, 192, 40)
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
        panel.place(tile, 24, top, 400, 52); return tile
    }
    private fun changeBrightness(delta: Int) {
        stageBrightness((pendingBrightness ?: AppCore.brightness()) + delta); render()
    }
    private fun stageBrightness(value: Int) {
        pendingBrightness = value.coerceIn(0, 100)
        handler.removeCallbacks(saveBrightness); handler.postDelayed(saveBrightness, 600)
        brightnessSlider.show(pendingBrightness!!)
    }
    private fun applyEnabled(enabled: Boolean) {
        if (enabled != AppCore.enabled()) {
            if (enabled) AppCore.setEnabled(true) else AppCore.setPower(false)
            Client.flow(this, enabled)
        }
        render()
    }
    private fun speedIndex() = when (AppCore.period()) { 1000 -> 0; 500 -> 1; else -> 2 }
    private fun setSpeed(index: Int) { AppCore.setPeriod(intArrayOf(1000, 500, 150)[index]); Client.refresh(this); render() }
    private fun changeSpeed(delta: Int) = setSpeed((speedIndex() + delta + 3) % 3)
    override fun onUiFrame(colors: IntArray) { ownColors = colors; updatePreview() }
    private fun updatePreview() {
        guarded {
            val layout = AppCore.layout()
            preview.update(ownColors ?: IntArray(9) { 0xb2b9c6 }, layout.geometry(), layout.reverse)
        }
    }
    private fun render() {
        if (powerSwitch.isChecked != AppCore.enabled()) {
            renderingSwitch = true; powerSwitch.isChecked = AppCore.enabled(); renderingSwitch = false
        }
        brightnessSlider.show(pendingBrightness ?: AppCore.brightness())
        speedChoices.forEachIndexed { i, view ->
            view.isSelected = i == speedIndex()
            view.setTextColor(if (i == speedIndex()) UiTheme.selectedText else UiTheme.secondary)
            view.typeface = if (i == speedIndex()) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        guarded {
            direction.value.text = if (AppCore.layout().reverse) "底部中央 → 右" else "底部中央 → 左"
            val engine = JSONObject(AppCore.state()).optJSONObject("engine")
            val identified = engine?.optString("device_name", "") ?: ""
            val displayName = if (identified.isNotBlank() && !engine?.optString("device_id", "").isNullOrBlank()) identified else "${Build.BRAND} · ${Build.MODEL}"
            device.text = displayName
            val present = LightAccess.present(this)
            val error = engine?.optString("error", "") ?: ""
            val model = engine?.optString("light_model", "") ?: ""
            val verified = present && model.isNotBlank() && error.isEmpty()
            connection.text = when { verified -> "●  已连接"; present -> "○  正在识别"; else -> "○  未连接" }
            connection.setTextColor(if (verified) UiTheme.success else UiTheme.secondary)
            controllerName.text = when { verified -> engine?.optString("light_name", "控制盒"); present -> "正在识别灯光控制盒"; else -> "连接USB灯光控制盒" }
            controllerModel.text = if (verified) model else ""
            status.text = when {
                !present -> "连接USB灯带，开始随影"
                !AppCore.enabled() -> "随影已关闭"
                error.isNotEmpty() -> error.take(70)
                ownColors != null && engine?.optBoolean("running", false) == true -> "正在同步 · ${listOf("低", "中", "快")[speedIndex()]}"
                else -> "准备随影画面…"
            }
            val reporting = AppCore.enabled() && engine?.optBoolean("running", false) == true
            latency.text = if (reporting) "间隔 ${engine?.optInt("output_interval_ms", 0)} ms" else "同步已停止"
            sweepLatency.text = if (reporting) "九区轮换 ${engine?.optInt("actual_period_ms", 0)} ms" else "— ms"
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
