package com.brahma.connect.commands

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import com.brahma.connect.accessibility.BrahmaAccessibilityService
import com.brahma.connect.core.CommandResult
import com.brahma.connect.device.AndroidDeviceInfoProvider
import java.io.File

class DeviceCommandHandler(private val context: Context) {

    private val infoProvider = AndroidDeviceInfoProvider(context)

    fun handle(action: String, parameters: Map<String, Any?>): CommandResult {
        return try {
            when (action) {
                "get_device_info", "device_info" -> getDeviceInfo()
                "get_battery", "battery" -> getBattery()
                "flashlight" -> flashlight(parameters["enabled"] as? Boolean ?: true)
                "launch_app" -> launchApp(parameters["package"] as? String ?: parameters["app"] as? String ?: "")
                "open_url" -> openUrl(parameters["url"] as? String ?: "")
                "volume_get" -> volumeGet()
                "volume_set" -> volumeSet((parameters["level"] as? Number)?.toInt() ?: 50)
                "unlock_phone" -> unlockPhone(parameters["pin"] as? String)
                "file_list" -> fileList(parameters["path"] as? String ?: "")
                "file_read" -> fileRead(parameters["path"] as? String ?: "")
                "file_write" -> fileWrite(parameters["path"] as? String ?: "", parameters["content"] as? String ?: "")
                "file_delete" -> fileDelete(parameters["path"] as? String ?: "")
                else -> CommandResult(success = false, errorCode = "UNKNOWN_ACTION", error = "Action $action not recognized")
            }
        } catch (e: Exception) {
            CommandResult(success = false, errorCode = "EXECUTION_ERROR", error = e.localizedMessage)
        }
    }

    private fun getDeviceInfo(): CommandResult {
        val snap = infoProvider.snapshot
        return CommandResult(
            success = true,
            data = mapOf(
                "deviceName" to snap.deviceName,
                "model" to snap.model,
                "androidVersion" to snap.androidVersion,
                "batteryPercentage" to snap.batteryPercentage,
                "charging" to snap.charging,
                "wifiEnabled" to snap.wifiEnabled
            )
        )
    }

    private fun getBattery(): CommandResult {
        val (battery, charging) = infoProvider.batterySnapshot
        return CommandResult(
            success = true,
            data = mapOf(
                "percentage" to battery,
                "charging" to charging
            )
        )
    }

    private fun flashlight(enabled: Boolean): CommandResult {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return CommandResult(success = false, error = "CameraManager unavailable")
        val cameraId = cm.cameraIdList.firstOrNull()
            ?: return CommandResult(success = false, error = "No camera found")
        cm.setTorchMode(cameraId, enabled)
        return CommandResult(success = true, data = mapOf("flashlight" to enabled))
    }

    private fun launchApp(requested: String): CommandResult {
        if (requested.isBlank()) return CommandResult(success = false, error = "App name or package required")
        val pm = context.packageManager
        val pkg = resolveLaunchablePackage(requested)
            ?: return CommandResult(success = false, error = "Could not find app matching $requested")

        val intent = pm.getLaunchIntentForPackage(pkg)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } ?: return CommandResult(success = false, error = "Cannot launch package $pkg")

        context.startActivity(intent)
        return CommandResult(success = true, data = mapOf("launched" to pkg))
    }

    private fun resolveLaunchablePackage(requested: String): String? {
        val pm = context.packageManager
        // If it's already an installed package
        try {
            pm.getPackageInfo(requested, 0)
            return requested
        } catch (_: Exception) {}

        val normalized = normalizeAppQuery(requested)
        val installed = pm.getInstalledApplications(0)
        for (app in installed) {
            val label = pm.getApplicationLabel(app).toString().lowercase()
            if (label == normalized || label.contains(normalized)) {
                if (pm.getLaunchIntentForPackage(app.packageName) != null) {
                    return app.packageName
                }
            }
        }
        return null
    }

    private fun normalizeAppQuery(value: String): String {
        return value.lowercase().trim()
    }

    private fun openUrl(url: String): CommandResult {
        if (url.isBlank()) return CommandResult(success = false, error = "URL required")
        val uri = if (!url.startsWith("http://") && !url.startsWith("https://")) "https://$url" else url
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return CommandResult(success = true, data = mapOf("opened" to uri))
    }

    private fun volumeGet(): CommandResult {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return CommandResult(success = false, error = "AudioManager unavailable")
        val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return CommandResult(
            success = true,
            data = mapOf("volume" to ((current.toFloat() / max.toFloat()) * 100).toInt())
        )
    }

    private fun volumeSet(level: Int): CommandResult {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return CommandResult(success = false, error = "AudioManager unavailable")
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = ((level.coerceIn(0, 100) / 100f) * max).toInt()
        am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        return CommandResult(success = true, data = mapOf("level" to level))
    }

    private fun unlockPhone(pin: String?): CommandResult {
        val service = BrahmaAccessibilityService.instance
            ?: return CommandResult(success = false, error = "Brahma Accessibility Service is not enabled.")
        val success = service.unlockPhone(pin)
        return CommandResult(success = success, data = mapOf("unlocked" to success))
    }

    private fun resolveFileTarget(path: String): File {
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        return if (path.isBlank()) baseDir else File(baseDir, path.removePrefix("/"))
    }

    private fun fileList(path: String): CommandResult {
        val target = resolveFileTarget(path)
        if (!target.exists()) return CommandResult(success = false, error = "Path does not exist")
        val files = target.listFiles()?.map {
            mapOf("name" to it.name, "isDirectory" to it.isDirectory, "size" to it.length())
        } ?: emptyList()
        return CommandResult(success = true, data = mapOf("files" to files))
    }

    private fun fileRead(path: String): CommandResult {
        val target = resolveFileTarget(path)
        if (!target.exists() || target.isDirectory) return CommandResult(success = false, error = "File not found")
        val content = target.readText()
        return CommandResult(success = true, data = mapOf("content" to content))
    }

    private fun fileWrite(path: String, content: String): CommandResult {
        val target = resolveFileTarget(path)
        target.parentFile?.mkdirs()
        target.writeText(content)
        return CommandResult(success = true, data = mapOf("written" to target.length()))
    }

    private fun fileDelete(path: String): CommandResult {
        val target = resolveFileTarget(path)
        val deleted = target.delete()
        return CommandResult(success = deleted, data = mapOf("deleted" to deleted))
    }
}
