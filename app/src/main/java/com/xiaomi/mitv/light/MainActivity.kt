package com.xiaomi.mitv.light

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.*
import android.view.*
import android.widget.*
import com.xiaomi.mitv.light.runtime.LightAccess
import com.xiaomi.mitv.light.tcl.*
import com.xiaomi.mitv.light.ui.*
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var backdrop: AmbientBackdrop
    private lateinit var preview: StripPreview
    private lateinit var power: TextView
    private lateinit var brightness: TextView
    private lateinit var speed: TextView
    private lateinit var direction: TextView
    private lateinit var device: TextView
    private lateinit var status: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var pendingBrightness: Int? = null
    private val saveBrightness = Runnable { pendingBrightness?.let { AppCore.setBrightness(it); Client.refresh(this) }; pendingBrightness = null }
    private val refresh = object : Runnable { override fun run() { render(); handler.postDelayed(this, 250) } }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val root = FrameLayout(this)
        backdrop = AmbientBackdrop(this); root.addView(backdrop, FrameLayout.LayoutParams(-1, -1))
        val scroll = ScrollView(this).apply { isFillViewport = false; clipToPadding = false }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(32), dp(22), dp(32), dp(18))
            background = rounded(0xc5141725.toInt(), 0x28ffffff, 22)
        }
        val screen = resources.displayMetrics
        val cardWidth = minOf(dp(620), screen.widthPixels - dp(48))
        scroll.addView(card); root.addView(scroll, FrameLayout.LayoutParams(cardWidth, -2, Gravity.CENTER).apply { topMargin = dp(20); bottomMargin = dp(20); height = minOf(screen.heightPixels - dp(40), dp(600)) })
        card.addView(text("映光  FrameLuma", 27).apply { typeface = Typeface.DEFAULT_BOLD })
        device = text("TCL M01 / G10 · C3APRO", 13, 0xb3ffffff.toInt()); card.addView(device)
        preview = StripPreview(this); card.addView(preview, LinearLayout.LayoutParams(dp(210), dp(118)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(12); bottomMargin = dp(10) })
        power = row(card, "灯带随影", {
            val enabled = !AppCore.enabled()
            if (enabled) AppCore.setEnabled(true) else AppCore.setPower(false)
            Client.flow(this, enabled); render()
        })
        brightness = row(card, "亮度", {})
        (brightness.parent as View).setOnKeyListener { _, code, event ->
            if (event.action != KeyEvent.ACTION_DOWN || code !in intArrayOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)) false
            else {
                pendingBrightness = ((pendingBrightness ?: AppCore.brightness()) + if (code == KeyEvent.KEYCODE_DPAD_LEFT) -5 else 5).coerceIn(1, 100)
                handler.removeCallbacks(saveBrightness); handler.postDelayed(saveBrightness, 600); render(); true
            }
        }
        speed = row(card, "更新速度", { changeSpeed(1) })
        (speed.parent as View).setOnKeyListener { _, code, e ->
            if (e.action == KeyEvent.ACTION_DOWN && code in intArrayOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)) { changeSpeed(if (code == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1); true } else false
        }
        direction = row(card, "安装方向", {
            guarded { AppCore.setLayout(AppCore.layout().json().put("reverse", !AppCore.layout().reverse)); Client.refresh(this) }; render()
        })
        row(card, "高级校准", { startActivity(Intent(this, CalibrationActivity::class.java)) }).text = "可选  ›"
        row(card, "设备与开源说明", { showDetails() }).text = "›"
        status = text("等待采样状态", 12, 0x99ffffff.toInt()).apply { gravity = Gravity.CENTER; setPadding(0, dp(8), 0, 0) }; card.addView(status)
        setContentView(root)
        (power.parent as View).requestFocus()
        render()
    }
    private fun changeSpeed(delta: Int) {
        val values = intArrayOf(1000, 500, 150); val at = values.indexOf(AppCore.period()).takeIf { it >= 0 } ?: 2
        AppCore.setPeriod(values[(at + delta + values.size) % values.size]); Client.refresh(this); render()
    }
    private fun render() {
        power.text = if (AppCore.enabled()) "开" else "关"
        brightness.text = "◀  ${pendingBrightness ?: AppCore.brightness()}%  ▶"
        speed.text = when (AppCore.period()) { 1000 -> "节能"; 500 -> "均衡"; else -> "流畅 · 分批更新" }
        guarded {
            val layout = AppCore.layout()
            direction.text = if (layout.reverse) "底部中央向右" else "底部中央向左"
            val state = JSONObject(AppCore.state()); val engine = state.optJSONObject("engine")
            val c = engine?.optJSONArray("colors")
            preview.update(IntArray(9) { c?.optInt(it, 0x334050) ?: 0x334050 }, layout.geometry(), layout.reverse)
            val identified = engine?.optString("device_name", "") ?: ""
            device.text = if (identified.isNotBlank() && !engine?.optString("device_id", "").isNullOrEmpty()) "$identified · Project ${engine?.optInt("project_id", 0)}" else "${Build.BRAND} ${Build.PRODUCT} / ${Build.DEVICE} · ${Build.MODEL}"
            val error = engine?.optString("error", "") ?: ""
            status.text = when {
                !LightAccess.present(this) -> "未连接支持的USB灯带 · 初始布局尚未贴装校准"
                error.isNotEmpty() -> error.take(100)
                !AppCore.enabled() -> "随影已关闭 · 取色与USB由宿主停止释放"
                engine?.optBoolean("running", false) == true -> "更新间隔 ${engine.optInt("output_interval_ms")}ms  ·  九区一轮 ${engine.optInt("actual_period_ms")}ms"
                else -> "等待TCL宿主初始化 · 保持原有机型接入条件"
            }
        }
    }
    private fun showDetails() {
        val message = "设备：${Build.BRAND} / ${Build.PRODUCT} / ${Build.DEVICE}\n系统型号：${Build.MODEL}\n\n首期适配：TCL 27C2A-S / V064 / Project32；米家philips.light.strip3 / 2m。DP链路已有原型验证；HDMI与内置系统的完整验证仍待完成。\n\n本项目Apache-2.0。参考tv-ambient的原生电视交互、Hyperion与LumiSync模块设计、screen_strip_sync协议资料。第三方许可见仓库THIRD_PARTY_NOTICES.md。"
        AlertDialog.Builder(this).setTitle("FrameLuma · 映光").setMessage(message).setPositiveButton("知道了", null).setNeutralButton("开源许可") { _, _ ->
            val entries = assets.list("licenses")?.sorted()?.toTypedArray() ?: emptyArray()
            AlertDialog.Builder(this).setTitle("许可与来源").setItems(entries) { _, i ->
                val content = assets.open("licenses/${entries[i]}").bufferedReader().use { it.readText() }
                AlertDialog.Builder(this).setTitle(entries[i]).setMessage(content).setPositiveButton("返回", null).show()
            }.show()
        }.show()
    }
    private fun guarded(action: () -> Unit) { try { action() } catch (e: Exception) { if (::status.isInitialized) status.text = e.message ?: "配置不可用" } }
    private fun text(value: String, size: Int, color: Int = Color.WHITE) = TextView(this).apply { text = value; textSize = size.toFloat(); setTextColor(color) }
    private fun row(parent: LinearLayout, label: String, action: () -> Unit): TextView {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; isFocusable = true; isClickable = true; setPadding(dp(18), 0, dp(18), 0) }
        row.background = StateListDrawable().apply { addState(intArrayOf(android.R.attr.state_focused), rounded(0xff2469b7.toInt(), 0x70ffffff, 9)); addState(intArrayOf(), rounded(0x19ffffff, 0x30ffffff, 9)) }
        row.addView(text(label, 17), LinearLayout.LayoutParams(0, -2, 1f))
        val value = text("", 16, 0xe6ffffff.toInt()); row.addView(value)
        parent.addView(row, LinearLayout.LayoutParams(-1, dp(46)).apply { bottomMargin = dp(7) })
        row.setOnClickListener { action() }; return value
    }
    private fun rounded(fill: Int, stroke: Int, radius: Int) = GradientDrawable().apply { setColor(fill); cornerRadius = dp(radius).toFloat(); setStroke(dp(1), stroke) }
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    override fun onResume() { super.onResume(); backdrop.active = true; AppCore.setUiVisible(true); Client.refresh(this); Client.send(this, "STATUS"); handler.post(refresh); if (AppCore.enabled()) Client.flow(this, true) }
    override fun onPause() { handler.removeCallbacks(refresh); handler.removeCallbacks(saveBrightness); saveBrightness.run(); backdrop.active = false; AppCore.setUiVisible(false); Client.refresh(this); super.onPause() }
}
