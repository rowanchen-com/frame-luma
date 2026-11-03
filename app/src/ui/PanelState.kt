package frameluma.app.ui

import frameluma.runtime.LayoutOptions
import org.json.JSONArray
import org.json.JSONObject

enum class Panel {
    CALIBRATION,
    ABOUT,
    LICENSES,
    RECOVERY,
}

data class CalibrationDraft(
    val mode: String,
    val units: Int,
    val counts: List<Int>,
    val head: Int,
    val tail: Int,
    val offset: Int,
    val reverse: Boolean,
) {
    val total
        get() = counts.sum() + head + tail

    fun options() =
        LayoutOptions(
            JSONObject()
                .put("mode", mode)
                .put("unit_count", units)
                .put("counts", JSONArray(counts))
                .put("off_head", head)
                .put("off_tail", tail)
                .put("offset_units", offset)
                .put("reverse", reverse)
        )

    companion object {
        fun from(value: LayoutOptions) =
            CalibrationDraft(
                value.mode,
                value.units,
                value.counts.toList(),
                value.offHead,
                value.offTail,
                value.offset,
                value.reverse,
            )
    }
}

/** Titles are resolved with the current device language when the page is created. */
sealed interface Overlay {
    val title: String

    data class Loading(override val title: String) : Overlay

    data class Failure(override val title: String, val message: String) : Overlay

    data class About(val version: String, override val title: String) : Overlay

    data class Licenses(override val title: String) : Overlay
    data class Recovery(override val title: String) : Overlay

    data class Reader(override val title: String, val body: String) : Overlay

    data class Calibration(
        val draft: CalibrationDraft,
        val labels: List<String>,
        val hint: String,
        val error: String = "",
        override val title: String,
    ) : Overlay

    data class Locator(
        val units: Int,
        val labels: List<String>,
        val note: String,
        override val title: String,
        val unit: Int = 1,
        val zone: Int = 1,
    ) : Overlay

    data class Reset(override val title: String) : Overlay
}
