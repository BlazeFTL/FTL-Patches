package app.ftl.patches.removeads

private val CAMEL_BOUNDARY = Regex("(?<=[a-z0-9])(?=[A-Z])")
private val AD_WORDS = setOf("ad", "ads", "banner", "nativead")
private val AD_BLOCK_WORDS = setOf("block", "blocker", "blocking")

internal fun idLooksAdRelated(id: String): Boolean {
    val tokens = id.split('_', '-').flatMap { it.split(CAMEL_BOUNDARY) }.map { it.lowercase() }
    if (tokens.any { it in AD_BLOCK_WORDS }) return false
    return tokens.any { it in AD_WORDS }
}
