package com.dergoogler.mmrl.service

object ServiceCadencePolicy {
    const val MIN_HOURS = 1L
    const val MAX_HOURS = 168L
    fun clampHours(value: Long): Long = value.coerceIn(MIN_HOURS, MAX_HOURS)
}
