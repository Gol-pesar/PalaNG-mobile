package com.example.xrayclient.domain.parsers

import com.example.xrayclient.data.models.ServerProfile

sealed interface ParseOutcome {
    data class Success(val profile: ServerProfile) : ParseOutcome
    data class Failure(val reason: String, val rawInput: String) : ParseOutcome
}

/**
 * Extensible parser contract for individual proxy URI schemes or config formats.
 */
interface ProxyUriParser {
    val supportedSchemes: Set<String>
    fun canParse(raw: String): Boolean
    fun parse(raw: String): ParseOutcome
}
