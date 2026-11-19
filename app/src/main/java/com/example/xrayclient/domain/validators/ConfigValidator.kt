package com.example.xrayclient.domain.validators

import com.example.xrayclient.data.models.ProxyProtocol
import com.example.xrayclient.data.models.SecurityType
import com.example.xrayclient.data.models.ServerProfile
import com.example.xrayclient.utils.NetworkUtils
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

sealed interface ValidationResult {
    data object Valid : ValidationResult
    data class Invalid(val errors: List<String>) : ValidationResult {
        val message: String get() = errors.joinToString("\n")
    }
}

object ConfigValidator {

    private val UUID_REGEX = Regex(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    )

    val SUPPORTED_SS_METHODS = setOf(
        "aes-128-gcm",
        "aes-256-gcm",
        "chacha20-ietf-poly1305",
        "chacha20-poly1305",
        "xchacha20-ietf-poly1305",
        "xchacha20-poly1305",
        "2022-blake3-aes-128-gcm",
        "2022-blake3-aes-256-gcm",
        "2022-blake3-chacha20-poly1305",
        "none",
        "plain",
        "rc4-md5",
        "aes-128-cfb",
        "aes-256-cfb",
        "aes-128-ctr",
        "aes-256-ctr"
    )

    fun validateProfile(profile: ServerProfile): ValidationResult {
        val errors = mutableListOf<String>()

        if (profile.protocol == ProxyProtocol.AMIR) {
            if (profile.address.isNotBlank() && !NetworkUtils.isValidHostOrIp(profile.address)) {
                errors += "آدرس CLEAN IP نامعتبر است."
            }
            if (profile.host.isBlank()) {
                errors += "وارد کردن WORKER URL برای کانفیگ AMIR الزامی است."
            }
            if (profile.uuid.isBlank()) {
                errors += "وارد کردن UUID برای کانفیگ AMIR الزامی است."
            }
            if (profile.port !in 1..65535) {
                errors += "شماره پورت باید بین ۱ تا ۶۵۵۳۵ باشد."
            }
            return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
        }

        if (profile.address.isBlank() || !NetworkUtils.isValidHostOrIp(profile.address)) {
            errors += "آدرس سرور نامعتبر است."
        }
        if (profile.port !in 1..65535) {
            errors += "شماره پورت باید بین ۱ تا ۶۵۵۳۵ باشد."
        }

        when (profile.protocol) {
            ProxyProtocol.VLESS -> {
                if (!UUID_REGEX.matches(profile.uuid.trim())) {
                    errors += "شناسه UUID برای VLESS نامعتبر است."
                }
                if (profile.security == SecurityType.REALITY && profile.realityPublicKey.isBlank()) {
                    errors += "برای امنیت REALITY وارد کردن کلید عمومی (pbk) الزامی است."
                }
            }
            ProxyProtocol.VMESS -> {
                if (!UUID_REGEX.matches(profile.uuid.trim())) {
                    errors += "شناسه UUID برای VMess نامعتبر است."
                }
                if (profile.alterId < 0) {
                    errors += "مقدار AlterID نمی‌تواند منفی باشد."
                }
            }
            ProxyProtocol.TROJAN -> {
                if (profile.password.isBlank()) {
                    errors += "رمز عبور برای Trojan نمی‌تواند خالی باشد."
                }
            }
            ProxyProtocol.SHADOWSOCKS -> {
                val method = profile.ssMethod.trim().lowercase()
                if (method !in SUPPORTED_SS_METHODS) {
                    errors += "روش رمزنگاری Shadowsocks پشتیبانی نمی‌شود: ${profile.ssMethod}"
                }
                if (profile.password.isBlank() && method !in setOf("none", "plain")) {
                    errors += "رمز عبور Shadowsocks نمی‌تواند خالی باشد."
                }
            }
            ProxyProtocol.HTTP, ProxyProtocol.SOCKS5, ProxyProtocol.AMIR -> Unit
        }

        return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
    }

    /**
     * Validates generated or imported Xray JSON structure before passing to native engine.
     */
    fun validateXrayJson(jsonString: String): ValidationResult {
        if (jsonString.isBlank()) {
            return ValidationResult.Invalid(listOf("پیکربندی JSON خالی است."))
        }
        return try {
            val root = Json.parseToJsonElement(jsonString) as? JsonObject
                ?: return ValidationResult.Invalid(listOf("ریشه JSON باید یک آبجکت باشد."))
            val errors = mutableListOf<String>()
            val outbounds = root["outbounds"] as? JsonArray
            if (outbounds == null || outbounds.isEmpty()) {
                errors += "بخش outbounds در فایل JSON وجود ندارد یا خالی است."
            } else {
                val first = outbounds[0].jsonObject
                val protocol = first["protocol"]?.jsonPrimitive?.contentOrNull.orEmpty()
                if (protocol.isBlank()) {
                    errors += "پروتکل outbound اصلی در JSON مشخص نشده است."
                }
            }
            if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
        } catch (e: Exception) {
            ValidationResult.Invalid(listOf("ساختار JSON نامعتبر است: ${e.message ?: "خطای نحوی"}"))
        }
    }
}
