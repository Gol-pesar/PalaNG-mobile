package com.example.xrayclient.domain.parsers

import com.example.xrayclient.data.models.ServerProfile
import com.example.xrayclient.utils.Base64Utils

data class BatchImportResult(
    val profiles: List<ServerProfile>,
    val subscriptionUrls: List<String>,
    val errors: List<String>,
    val duplicateCount: Int
)

/**
 * Extensible registry of proxy URI parsers and batch text/Base64/JSON importer.
 */
class ProxyLinkParser(
    private val parsers: List<ProxyUriParser> = defaultParsers()
) {
    fun parseSingle(rawLine: String): ParseOutcome {
        val trimmed = rawLine.trim()
        if (trimmed.isEmpty()) {
            return ParseOutcome.Failure("خط ورودی خالی است.", rawLine)
        }
        val parser = parsers.firstOrNull { it.canParse(trimmed) }
            ?: return ParseOutcome.Failure(
                "فرمت لینک پشتیبانی نمی‌شود: ${trimmed.substringBefore("://", trimmed.take(16))}",
                rawLine
            )
        return parser.parse(trimmed)
    }

    /**
     * Parses mixed input containing:
     * - Single or multiple proxy links (vless://, vmess://, trojan://, ss://, socks://, socks5://, http://, https://)
     * - Base64-encoded subscription content (standard, URL-safe, or unpadded)
     * - Xray JSON configs
     * - Subscription URLs
     */
    fun importBatch(
        rawInput: String,
        existingFingerprints: Set<String> = emptySet(),
        subscriptionId: String? = null
    ): BatchImportResult {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            return BatchImportResult(emptyList(), emptyList(), listOf("ورودی خالی است."), 0)
        }

        // 1. JSON file/snippet
        if (XrayJsonConfigParser.looksLikeJson(trimmed)) {
            return handleJsonOutcomes(XrayJsonConfigParser.parse(trimmed), existingFingerprints, subscriptionId)
        }

        // 2. Base64-encoded subscription payload (not a single vmess:// or ss:// URI)
        val expandedText = if (Base64Utils.looksLikeBase64(trimmed)) {
            Base64Utils.decodeToString(trimmed)?.takeIf { it.contains("://") || XrayJsonConfigParser.looksLikeJson(it) }
                ?: trimmed
        } else {
            trimmed
        }

        if (XrayJsonConfigParser.looksLikeJson(expandedText)) {
            return handleJsonOutcomes(XrayJsonConfigParser.parse(expandedText), existingFingerprints, subscriptionId)
        }

        // 3. Line-by-line (also split whitespace-separated links if pasted in one line)
        val lines = expandedText
            .split('\n', '\r')
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("//") }

        val profiles = mutableListOf<ServerProfile>()
        val subUrls = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val seen = existingFingerprints.toMutableSet()
        var duplicates = 0

        for (line in lines) {
            if ((line.startsWith("http://", true) || line.startsWith("https://", true)) &&
                HttpProxyParser.looksLikeSubscriptionUrl(line)
            ) {
                subUrls += line
                continue
            }

            when (val outcome = parseSingle(line)) {
                is ParseOutcome.Success -> {
                    val profile = outcome.profile.copy(subscriptionId = subscriptionId)
                    val fp = profile.duplicateFingerprint()
                    if (seen.add(fp)) {
                        profiles += profile
                    } else {
                        duplicates++
                    }
                }
                is ParseOutcome.Failure -> {
                    errors += outcome.reason
                }
            }
        }

        return BatchImportResult(
            profiles = profiles,
            subscriptionUrls = subUrls.distinct(),
            errors = errors,
            duplicateCount = duplicates
        )
    }

    private fun handleJsonOutcomes(
        outcomes: List<ParseOutcome>,
        existingFingerprints: Set<String>,
        subscriptionId: String?
    ): BatchImportResult {
        val profiles = mutableListOf<ServerProfile>()
        val errors = mutableListOf<String>()
        val seen = existingFingerprints.toMutableSet()
        var duplicates = 0

        for (outcome in outcomes) {
            when (outcome) {
                is ParseOutcome.Success -> {
                    val profile = outcome.profile.copy(subscriptionId = subscriptionId)
                    if (seen.add(profile.duplicateFingerprint())) {
                        profiles += profile
                    } else {
                        duplicates++
                    }
                }
                is ParseOutcome.Failure -> errors += outcome.reason
            }
        }
        return BatchImportResult(profiles, emptyList(), errors, duplicates)
    }

    companion object {
        fun defaultParsers(): List<ProxyUriParser> = listOf(
            VlessParser(),
            VmessParser(),
            TrojanParser(),
            ShadowsocksParser(),
            Socks5Parser(),
            HttpProxyParser(),
            AmirUriParser()
        )
    }
}
