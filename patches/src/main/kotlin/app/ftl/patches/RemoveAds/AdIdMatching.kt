package app.ftl.patches.ads

private val CAMEL_BOUNDARY = Regex("(?<=[a-z0-9])(?=[A-Z])")
private val AD_WORDS = setOf("ad", "ads", "banner", "nativead")

internal fun idLooksAdRelated(id: String): Boolean =
    id.split('_', '-')
        .flatMap { it.split(CAMEL_BOUNDARY) }
        .any { it.lowercase() in AD_WORDS }
