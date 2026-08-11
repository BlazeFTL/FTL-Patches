package app.ftl.extension.snaptube

import android.util.Log
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup

object SnaptubeSettingsHider {
    private const val TAG = "MorpheSnaptube"

    private val HIDDEN_CATEGORY_PREFIXES = listOf("Download tools", "Phone clean")

    private val HIDDEN_PREFERENCE_KEYS = listOf(
        "recover_deleted_files_settings",
        "whatsapp_status_saver",
        "vault_settings",
        "clean_junk",
        "clean_boost",
        "clean_battery_saver",
        "clean_large_files",
        "clean_trash",
        "clean_whatsapp",
        "photo_clean",
        "clean_app_uninstaller",
    )

    @JvmStatic
    fun hideCategories(screen: PreferenceGroup?) {
        if (screen == null) return
        try {
            val getCount = PreferenceGroup::class.java.getMethod("J0")
            val getPref = PreferenceGroup::class.java.getMethod("I0", Int::class.javaPrimitiveType)
            val removePref = PreferenceGroup::class.java.getMethod("M0", Preference::class.java)
            val getTitle = Preference::class.java.getMethod("C")

            var i = 0
            while (i < (getCount.invoke(screen) as Int)) {
                val pref = getPref.invoke(screen, i) as Preference
                val title = (getTitle.invoke(pref) as? CharSequence)?.toString()

                if (title != null && HIDDEN_CATEGORY_PREFIXES.any { title.startsWith(it) }) {
                    removePref.invoke(screen, pref)
                } else {
                    i++
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "hideCategories failed, target methods may have been renamed", t)
        }
    }

    @JvmStatic
    fun defaultChannelEnabled(channelId: String?): Boolean =
        channelId != "Channel_Id_Push" && channelId != "Channel_Id_Cleaner"

    @JvmStatic
    fun hidePreferences(fragment: PreferenceFragmentCompat) {
        try {
            val findPref = PreferenceFragmentCompat::class.java.getMethod("w1", CharSequence::class.java)
            val setVisible = Preference::class.java.getMethod("x0", Boolean::class.javaPrimitiveType)

            for (key in HIDDEN_PREFERENCE_KEYS) {
                val pref = findPref.invoke(fragment, key) as? Preference ?: continue
                setVisible.invoke(pref, false)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "hidePreferences failed, target methods may have been renamed", t)
        }
    }
}
