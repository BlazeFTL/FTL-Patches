package app.ftl.patches.removeadslite.hosts

import java.io.File
import java.net.IDN
import java.net.URI

class HostsBlocker private constructor(
    private val blocklist: Map<String, String?>,
) {
    /**
     * Checks if the given [value] (a URL or bare hostname) is blocked based on the blocklist.
     *
     * When [wildcard] is true (default), subdomain matching is performed. For example, if
     * "example.com" is in the blocklist, then "example.com", "www.example.com", and
     * "sub.www.example.com" would all be considered blocked. When [wildcard] is false,
     * only exact host matches will be considered blocked.
     *
     * If a blocklist entry has a path prefix (e.g. "example.com/ads"), [value] is only
     * considered blocked when its path also starts with that prefix - the rest of
     * "example.com" is left alone.
     */
    fun isBlocked(
        value: String,
        wildcard: Boolean = true,
    ): Boolean {
        if (value.isBlank()) return false

        val uri = toUri(value) ?: return false
        var normalizedHost = normalizeDomain(uri.host ?: return false)
            .takeIf(::isHostValid)
            ?: return false

        val path = uri.path.orEmpty()

        if (blocklist.containsKey(normalizedHost)) {
            val requiredPath = blocklist.getValue(normalizedHost)
            return requiredPath == null || path.startsWith(requiredPath, ignoreCase = true)
        }
        if (!wildcard) return false

        while (normalizedHost.contains(DOT_CHAR)) {
            normalizedHost = normalizedHost.substringAfter(DOT_CHAR)
            if (blocklist.containsKey(normalizedHost)) {
                val requiredPath = blocklist.getValue(normalizedHost)
                return requiredPath == null || path.startsWith(requiredPath, ignoreCase = true)
            }
        }
        return false
    }

    /**
     * Normalizes a domain name to its ASCII-compatible form (Punycode) and in lowercase.
     */
    private fun normalizeDomain(domain: String): String {
        val trimmedDomain = domain.trim().trimEnd('.')
        return runCatching { IDN.toASCII(trimmedDomain) }
            .getOrDefault(domain)
            .lowercase()
    }

    companion object {
        private const val COMMENT_CHAR = '#'
        private const val SPACE_CHAR = ' '
        private const val DOT_CHAR = '.'

        private const val MAX_DOMAIN_LENGTH = 253
        private const val MAX_PARTS = 127
        private const val MIN_PARTS = 2
        private const val MAX_PARTS_LENGTH = 63

        private val RESERVED_HOSTNAMES = setOf(
            "localhost",
            "localhost6",
            "localhost.localdomain",
            "localhost6.localdomain6",
            "local",
            "broadcasthost",

            // IPv4
            "127.0.0.1",
            "0.0.0.0",

            // IPv6
            "::1",
            "ip6-localhost",
            "ip6-loopback",
            "ip6-localnet",
            "ip6-mcastprefix",
            "ip6-allnodes",
            "ip6-allrouters",
            "ip6-allhosts",
        )

        /**
         * Creates a [HostsBlocker] instance by parsing a blocklist from the
         * given multiline [input] string (standard hosts-file format).
         */
        fun fromString(input: String): HostsBlocker {
            val blocklist = mutableMapOf<String, String?>()
            parseLines(input.lineSequence(), blocklist)
            return HostsBlocker(blocklist)
        }

        /**
         * Creates a [HostsBlocker] instance by parsing a blocklist from the given [file]
         * (standard hosts-file format).
         */
        fun fromFile(file: File): HostsBlocker = fromString(file.readText())

        /**
         * Merges multiple [HostsBlocker] instances into one. Later entries override earlier
         * ones on host collision (e.g. a user-supplied file can widen/override a path-scoped
         * bundled entry with a bare host).
         */
        fun merge(vararg blockers: HostsBlocker): HostsBlocker {
            val merged = mutableMapOf<String, String?>()
            blockers.forEach { merged.putAll(it.blocklist) }
            return HostsBlocker(merged)
        }

        /**
         * Extracts the host from a given [input] string, which may be a URL or a hostname.
         * Ignores any path - use for redirecting the matched host substring, not for
         * blocklist lookups (those go through [HostsBlocker.isBlocked]).
         */
        fun extractHost(input: String): String? = toUri(input)?.host

        private fun toUri(input: String): URI? {
            val urlWithScheme = if (input.contains("://")) input else "http://$input"
            return runCatching { URI.create(urlWithScheme) }.getOrNull()
        }

        private fun parseLines(
            lines: Sequence<String>,
            out: MutableMap<String, String?>,
        ) {
            for (line in lines) {
                // 0.0.0.0 example.com # comment here -> 0.0.0.0 example.com
                // example.com # comment here         -> example.com
                // # comment only                     -> (ignored)
                val trimmed = line.substringBefore(COMMENT_CHAR).trim()
                if (trimmed.isBlank()) continue

                // 0.0.0.0 example.com     -> host=example.com, path=null
                // https://example.com/ads -> host=example.com, path=/ads
                // http://ads.example.com  -> host=ads.example.com, path=null
                val entry = trimmed.substringAfter(SPACE_CHAR).trim()
                val uri = toUri(entry) ?: continue
                val host = uri.host?.lowercase() ?: continue

                if (host in RESERVED_HOSTNAMES) continue
                if (!isHostValid(host)) continue

                val path = uri.path?.takeIf { it.isNotEmpty() && it != "/" }
                out[host] = path
            }
        }

        private fun isHostValid(input: String): Boolean {
            if (input.isBlank() || input.length > MAX_DOMAIN_LENGTH) return false
            if (input.startsWith(DOT_CHAR) || input.endsWith(DOT_CHAR)) return false

            val parts = input.split(DOT_CHAR)
            if (parts.size !in MIN_PARTS..MAX_PARTS) return false

            for (part in parts) {
                if (part.length > MAX_PARTS_LENGTH) return false
            }
            return true
        }
    }
}
