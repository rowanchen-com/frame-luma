package frameluma.app.ui

/** Short monotonic interpolation between actual player readings; buffering never advances colours. */
internal class MediaTimeline(private val readPosition: () -> Long) {
    private var position = 0L
    private var anchoredAt = -1L
    private var buffering = false

    fun current(now: Long): Long {
        if (buffering) return position
        if (anchoredAt < 0 || now - anchoredAt >= SYNC_INTERVAL_MS) refresh(now)
        return (position + (now - anchoredAt).coerceAtLeast(0)) % BackdropMotion.CYCLE_MS
    }

    fun refresh(now: Long) {
        position = readPosition().coerceAtLeast(0) % BackdropMotion.CYCLE_MS
        anchoredAt = now
    }

    fun buffering(value: Boolean, now: Long) {
        if (value == buffering) return
        if (value) position = current(now)
        buffering = value
        if (!value) refresh(now)
    }

    companion object {
        const val SYNC_INTERVAL_MS = 1000L
    }
}
