package app.ftl.patches.mxplayerad

import app.morphe.patcher.Fingerprint

private const val LOCAL_ME_PAGE_VIEW_MODEL_CLASS =
    "Lcom/mxtech/videoplayer/ad/subscriptions/ui/metab/viewmodels/LocalMePageViewModel;"

/**
 * Matches the method that builds the Me tab's local-tiles list. Anchored purely on the
 * tile row labels, which are real English strings passed straight into each row's data
 * class constructor - not obfuscated, unlike the method's own name. Order here is the
 * order the matches come back in via [Fingerprint.stringMatches].
 */
internal object LocalMeTilesFingerprint : Fingerprint(
    definingClass = LOCAL_ME_PAGE_VIEW_MODEL_CLASS,
    strings = listOf(
        "MX Share",
        "Private Folder",
        "Video Playlists",
        "Local Network",
        "Music Player",
        "Cloud Drive",
    ),
)
