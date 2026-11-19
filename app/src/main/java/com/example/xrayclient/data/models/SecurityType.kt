package com.example.xrayclient.data.models

enum class SecurityType(
    val xrayValue: String,
    val displayName: String
) {
    NONE("none", "بدون رمزنگاری"),
    TLS("tls", "TLS"),
    REALITY("reality", "REALITY");

    companion object {
        fun fromRaw(raw: String?): SecurityType = when (raw?.trim()?.lowercase()) {
            "tls" -> TLS
            "reality" -> REALITY
            else -> NONE
        }
    }
}
