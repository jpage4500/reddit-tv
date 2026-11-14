package com.jpage4500.reddittv.util

import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * process-wide CPU usage, as a field for the per-stream `stats:` lines.
 *
 * this is the process-level counterpart to the per-stream numbers on those lines. it is deliberately
 * NOT per-player: only desktop can attribute CPU to a single stream (its play thread does the whole
 * pixel path in-process), while on android MediaCodec decodes into a Surface and SurfaceFlinger
 * composites, so the expensive work is billed to other processes entirely. a per-stream number there
 * would measure NAL buffer shuffling, read as ~0%, and invite a comparison against desktop's number
 * that it cannot support. what survives on every platform is "how hot is this app", so that is what
 * this reports.
 *
 * every playing stream prints the same value on its own line, which is why callers label it
 * `appCpu=` rather than `cpu=`: it is not this stream's share, and summing it across tiles would
 * report N times the real usage.
 *
 * cost is one platform CPU read per [MIN_SAMPLE_MS] no matter how many streams ask — the N stats
 * lines of a window land within a few ms of each other, so the first one samples and the rest print
 * what it published.
 */
@OptIn(ExperimentalAtomicApi::class)
object CpuSampler {
    // rate limit and noise floor in one: any shorter and the percentage is mostly sampling jitter.
    // well under the 10s stats cadence, so each window's first caller gets a fresh sample rather
    // than the previous window's
    private const val MIN_SAMPLE_MS = 1_000L

    // a gap this long means nobody asked for a while — video stopped, app backgrounded, screen off.
    // averaging across that reports a diluted percentage for an interval nobody was watching, so the
    // sample becomes a fresh baseline instead. 3x the stats cadence, so a couple of missed ticks
    // still report
    private const val MAX_SAMPLE_MS = 30_000L

    private const val NOT_AVAILABLE = "n/a"

    private val nextSampleMs = AtomicLong(0L)

    @Volatile
    private var lastCpuMs = -1L

    @Volatile
    private var lastWallMs = 0L

    @Volatile
    private var cached = NOT_AVAILABLE

    /**
     * percent of ONE core burned by the whole process, top-style, so >100% means more than a core's
     * worth and the value is directly comparable with the per-thread `cpu=` on desktop's stats line.
     * "n/a" until two samples exist. safe to call from any thread.
     */
    fun percent(): String {
        val now = DateUtils.nowMs()
        val due = nextSampleMs.load()
        // nowMs() is the wall clock, so it can jump backwards (ntp, user). without the second test
        // that would park `due` in the future and freeze this value until real time caught up
        val isDue = now >= due || now < lastWallMs
        // claim the sample before taking it so concurrent callers can't both take one. only the
        // winner gets past here, so the baseline fields below have a single writer per interval and
        // everyone else prints what the last winner published
        if (isDue && nextSampleMs.compareAndSet(due, now + MIN_SAMPLE_MS)) sample(now)
        return cached
    }

    private fun sample(now: Long) {
        val info = PlatformUtils.getCpuInfo()
        val prevCpuMs = lastCpuMs
        val prevWallMs = lastWallMs
        // leave a fresh baseline behind on every path, including the ones that report nothing, so
        // the next interval measures cleanly
        lastCpuMs = info.processCpuMs
        lastWallMs = now

        val wallMs = now - prevWallMs
        // no interval to divide by on the first sample. the range test rejects both a gap too long to
        // mean anything and one too short (or negative, after a clock jump) to divide by
        if (info.processCpuMs < 0 || prevCpuMs < 0 || wallMs !in MIN_SAMPLE_MS..MAX_SAMPLE_MS) {
            cached = NOT_AVAILABLE
            return
        }
        cached = "${(info.processCpuMs - prevCpuMs) * 100 / wallMs}%"
    }
}
