package main.components.audio

import kotlinx.browser.window
import main.components.AudioSettings
import main.components.world.WorldLoader
import org.w3c.dom.HTMLAudioElement
import org.w3c.dom.HTMLMediaElement
import kotlinx.browser.document

/**
 * Global music manager for world background music.
 * - Plays looping music for current world
 * - Fades in on start
 * - Switches between day/night tracks for outdoors worlds
 * - Respects global AudioSettings (music enabled)
 */
object MusicManager {
    private var audio: HTMLAudioElement? = null
    private var fadeIntervalId: Int? = null

    private var dayUrl: String? = null
    private var nightUrl: String? = null
    private var isOutdoors: Boolean = false
    private var isNight: Boolean? = null

    private var currentUrl: String? = null
    private val activeExternalMedia = mutableSetOf<HTMLMediaElement>()
    private var recordingCount = 0

    init {
        // React to global audio setting changes
        AudioSettings.addObserver {
            refreshPlayback()
        }

        // Catch any <audio> or <video> elements starting to play (e.g. from Md.kt or MediaLibrary)
        window.document.addEventListener("play", { ev ->
            val target = ev.target
            if (target is HTMLMediaElement && target != audio) {
                activeExternalMedia.add(target)
                refreshPlayback()
            }
        }, true)

        window.document.addEventListener("pause", { ev ->
            val target = ev.target
            if (target is HTMLMediaElement && target != audio) {
                activeExternalMedia.remove(target)
                refreshPlayback()
            }
        }, true)

        window.document.addEventListener("ended", { ev ->
            val target = ev.target
            if (target is HTMLMediaElement && target != audio) {
                activeExternalMedia.remove(target)
                refreshPlayback()
            }
        }, true)

        // Periodically refresh to catch elements removed from DOM without pause event
        window.setInterval({
            if (activeExternalMedia.isNotEmpty() || recordingCount > 0) {
                refreshPlayback()
            }
        }, 500)
    }

    private fun shouldBePlaying(): Boolean {
        return AudioSettings.isMusicEnabled() && activeExternalMedia.isEmpty() && recordingCount == 0
    }

    /**
     * Call when starting an audio recording (e.g. VoiceInput, AudioRecorder).
     * This will pause background music if it's playing.
     */
    fun startRecording() {
        recordingCount++
        refreshPlayback()
    }

    /**
     * Call when finishing an audio recording.
     */
    fun stopRecording() {
        recordingCount = (recordingCount - 1).coerceAtLeast(0)
        refreshPlayback()
    }

    private fun refreshPlayback() {
        try {
            // Cleanup activeExternalMedia: remove elements that are no longer playing or no longer in the document
            activeExternalMedia.removeAll { el ->
                val shouldRemove = el.paused || el.ended || document.body?.contains(el) == false
                if (shouldRemove && !el.paused && !el.ended) {
                    // It was removed from DOM but still playing; stop it!
                    try { el.pause() } catch (_: Throwable) {}
                }
                shouldRemove
            }

            val targetUrl = if (shouldBePlaying()) chooseUrl() else null

            if (targetUrl != null) {
                val a = audio
                // If already playing the correct track, don't interrupt it
                if (a != null && a.src == targetUrl && !a.paused) {
                    return
                }
                playUrl(targetUrl, fadeIn = true)
            } else {
                pause()
            }
        } catch (t: Throwable) {
            console.error("[MusicManager] Error refreshing playback", t)
        }
    }

    fun setWorldMusic(dayUrl: String?, nightUrl: String?, isOutdoors: Boolean) {
        this.dayUrl = dayUrl?.takeIf { it.isNotBlank() }
        this.nightUrl = nightUrl?.takeIf { it.isNotBlank() }
        this.isOutdoors = isOutdoors
        this.isNight = null // unknown until realtime sun updates (if outdoors)

        // Stop current playback; we'll restart when we know which one to play
        stop()

        if (!AudioSettings.isMusicEnabled()) return

        if (!isOutdoors) {
            // Indoor worlds: immediately play dayUrl if present
            this.dayUrl?.let { playUrl(it, fadeIn = true) }
        } else {
            // Outdoors: wait for setIsNight() to decide, but if both missing, do nothing
            val guess = this.dayUrl ?: this.nightUrl
            if (guess != null) {
                // Optionally start with day track until we know; better to wait for realtime callback
                // We'll do nothing here; start when setIsNight() is called
            }
        }
    }

    fun setIsNight(isNight: Boolean) {
        this.isNight = isNight
        if (!AudioSettings.isMusicEnabled()) return
        if (!isOutdoors) return // day/night not relevant
        val next = chooseUrl() ?: return
        if (next == currentUrl) return
        // Crossfade to new track
        crossfadeTo(next)
    }

    fun stop() {
        cancelFade()
        try {
            audio?.pause()
        } catch (_: Throwable) {}
        currentUrl = null
    }

    fun pause() {
        cancelFade()
        try { audio?.pause() } catch (_: Throwable) {}
    }

    private fun ensureAudio(): HTMLAudioElement {
        val a = audio ?: (document.createElement("audio") as HTMLAudioElement).also {
            it.loop = true
            it.volume = 0.0
            audio = it
        }
        return a
    }

    private fun chooseUrl(): String? {
        return if (isOutdoors) {
            val night = isNight
            if (night == true) nightUrl ?: dayUrl else dayUrl ?: nightUrl
        } else {
            dayUrl
        }
    }

    private fun playUrl(url: String, fadeIn: Boolean) {
        currentUrl = url
        if (!shouldBePlaying()) return

        try {
            val a = ensureAudio()
            if (a.src != url) {
                a.src = url
            }
            cancelFade()
            if (fadeIn) {
                a.volume = 0.0
            }
            a.loop = true
            a.play().catch { err ->
                console.warn("[MusicManager] Autoplay prevented or error starting audio: ${'$'}err")
            }
            if (fadeIn) fadeToVolume(1.0, durationMs = 1500)
            else a.volume = 1.0
        } catch (t: Throwable) {
            console.error("[MusicManager] Failed to play url", t)
        }
    }

    private fun crossfadeTo(url: String) {
        currentUrl = url
        if (!shouldBePlaying()) return

        try {
            val a = ensureAudio()
            if (a.paused) {
                // Not playing; just fade in new track
                playUrl(url, fadeIn = true)
                return
            }
            // Start fade out, then switch and fade in
            cancelFade()
            val startVol = a.volume
            val steps = 10
            var step = 0
            val interval = window.setInterval({
                step++
                val t = step.toDouble() / steps
                a.volume = (startVol * (1.0 - t)).coerceIn(0.0, 1.0)
                if (step >= steps) {
                    cancelFade()
                    a.pause()
                    a.src = url
                    a.volume = 0.0
                    a.play().catch { err ->
                        console.warn("[MusicManager] Autoplay prevented or error starting audio: ${'$'}err")
                    }
                    fadeToVolume(1.0, 1000)
                }
            }, 50)
            fadeIntervalId = interval
        } catch (t: Throwable) {
            console.error("[MusicManager] Failed crossfading", t)
            // Fallback: try to play directly
            playUrl(url, fadeIn = true)
        }
    }

    private fun fadeToVolume(target: Double, durationMs: Int) {
        try {
            val a = ensureAudio()
            cancelFade()
            val startVol = a.volume
            val delta = target - startVol
            val steps = maxOf(1, durationMs / 50)
            var step = 0
            fadeIntervalId = window.setInterval({
                step++
                val t = step.toDouble() / steps
                a.volume = (startVol + delta * t).coerceIn(0.0, 1.0)
                if (step >= steps) cancelFade()
            }, 50)
        } catch (t: Throwable) {
            console.error("[MusicManager] Failed fading volume", t)
        }
    }

    private fun cancelFade() {
        fadeIntervalId?.let {
            try { window.clearInterval(it) } catch (_: Throwable) {}
            fadeIntervalId = null
        }
    }
}
