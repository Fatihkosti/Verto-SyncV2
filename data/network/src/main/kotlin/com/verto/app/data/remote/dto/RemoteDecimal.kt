package com.verto.app.data.remote.dto

import com.verto.app.utils.BigDecimalSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal

fun Double.toRemoteDecimal(): BigDecimal = BigDecimal.valueOf(this)
fun BigDecimal.toRemoteDouble(): Double = toDouble()
