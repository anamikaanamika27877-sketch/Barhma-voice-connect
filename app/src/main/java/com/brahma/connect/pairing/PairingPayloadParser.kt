package com.brahma.connect.pairing

import com.brahma.connect.core.PairingOffer
import org.json.JSONObject

object PairingPayloadParser {
    fun parse(raw: String): PairingOffer? {
        if (raw.isBlank()) return null
        return try {
            val json = JSONObject(raw.trim())
            PairingOffer.fromJson(json)
        } catch (_: Exception) {
            null
        }
    }
}
