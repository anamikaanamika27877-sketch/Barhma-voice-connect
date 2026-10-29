package com.brahma.connect.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import com.brahma.connect.core.AgentStateStore

class BrahmaAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        AgentStateStore.addLog("Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Can be used to track active window changes or notifications if needed
    }

    override fun onInterrupt() {
        AgentStateStore.addLog("Accessibility service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
    }

    fun unlockPhone(pin: String?): Boolean {
        // Basic swipe-up gesture to show PIN keypad or unlock
        val swipePath = Path().apply {
            moveTo(500f, 1500f)
            lineTo(500f, 300f)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(swipePath, 0, 300))
            .build()

        return dispatchGesture(gesture, null, null)
    }

    companion object {
        var instance: BrahmaAccessibilityService? = null
            private set
    }
}
