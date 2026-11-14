package com.jpage4500.reddittv.util

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import com.jpage4500.reddittv.data.Branding
import java.awt.Desktop
import java.awt.Dimension
import java.awt.Window
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import javax.swing.SwingUtilities

private val log = LoggerConfig.createLogger("DesktopUtils")

object DesktopUtils {
    // where logs/devices/settings are saved for desktop — per-brand (see Branding.desktopHomeDir)
    val DESKTOP_HOME = Branding.desktopHomeDir

    fun getHomeDir(child: String? = null): File {
        val home = File(System.getProperty("user.home"), DESKTOP_HOME)
        if (!home.exists()) home.mkdirs()
        if (child == null) return home
        return File(home.absolutePath, child)
    }

    fun getDesktopOS(): DesktopOS {
        val osName = System.getProperty("os.name").lowercase()
        return when {
            osName.contains("mac") -> DesktopOS.MAC
            osName.contains("win") -> DesktopOS.WINDOWS
            else -> DesktopOS.LINUX
        }
    }

    fun isDesktopOS(vararg platformArr: DesktopOS): Boolean {
        return platformArr.contains(getDesktopOS())
    }

    fun getPlatformShortcut(key: Key): KeyShortcut {
        return if (isDesktopOS(DesktopOS.WINDOWS)) {
            // windows
            KeyShortcut(key, ctrl = true) // CTRL + Key
        } else {
            // mac / linux
            KeyShortcut(key, meta = true) // CMD + Key
        }
    }

    /**
     * Forces the provided window to the foreground. 
     * Specifically includes workarounds for macOS background-launch behavior.
     *
     * @param pinOnTop briefly pin the window always-on-top to force it over other apps. not at
     * launch: macOS 14+ refuses activation to an app the OS didn't launch (one started from the IDE),
     * so the pin only paints it over the active app for a moment and it then drops behind — a flash
     */
    fun bringWindowToFront(window: Window?, pinOnTop: Boolean = true) {
        if (window == null) return

        // 1. Tell the OS to bring the entire app process to the foreground.
        // This is crucial for apps launched via scripts/jdeploy on macOS.
        if (Desktop.isDesktopSupported()) {
            val desktop = Desktop.getDesktop()
            if (desktop.isSupported(Desktop.Action.APP_REQUEST_FOREGROUND)) {
                desktop.requestForeground(true)
            }
        }

        // 2. Manipulate the specific window to force it to the top of the z-order.
        window.apply {
            // Ensure the window is actually visible before we try to focus it
            if (!isVisible) {
                isVisible = true
            }

            // The macOS z-order hack:
            // Briefly pinning it as "always on top" forces the window manager 
            // to paint it over other applications, then we immediately turn it off.
            if (pinOnTop) isAlwaysOnTop = true
            toFront()
            requestFocus()
            if (pinOnTop) isAlwaysOnTop = false
        }
    }

    /**
     * nudges the most recently shown native window by 1px and back to force AWT
     * heavyweight components (e.g. JavaFX WebView via SwingPanel) to re-layout.
     *
     * needed because heavyweight components added during a Compose Dialog's first
     * composition can mount at the wrong bounds and only correct themselves when
     * the user manually moves/resizes the window. this synthesizes that event.
     */
    fun kickWindowRelayout() {
        SwingUtilities.invokeLater {
            val w = Window.getWindows().lastOrNull { it.isVisible } ?: return@invokeLater
            val s = w.size
            w.size = Dimension(s.width + 1, s.height)
            w.size = s
        }
    }

    /**
     * get the current log file
     */
    fun getLogFile(): File? {
        return try {
            val logsDir = DesktopUtils.getHomeDir(PATH_LOGS)
            File(logsDir, FILE_LOG)
        } catch (e: Exception) {
            log.e("getLogFile: Exception: ${e.message}")
            null
        }
    }
}