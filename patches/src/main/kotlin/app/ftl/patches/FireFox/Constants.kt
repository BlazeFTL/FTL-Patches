package app.ftl.patches.firefox

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

internal val COMPATIBILITY_FIREFOX_NIGHTLY = Compatibility(
    packageName = "org.mozilla.fenix",
    name = "Firefox Nightly",
    targets = listOf(
        AppTarget(version = "159.0a1", versionCode = 2016187231),
        AppTarget(version = "159.0a1", versionCode = 2016188279),
    ),
)

internal const val MOD_SETTINGS = "Lapp/ftl/extension/firefox/ModSettings;"
internal const val OLD_MENU = "Lapp/ftl/extension/firefox/OldMenu;"
internal const val MOD_COMPOSE = "Lapp/ftl/extension/firefox/ModCompose;"
internal const val MOD_LAMBDA = "Lapp/ftl/extension/firefox/ModLambda;"
internal const val MOD_CLICK = "Lapp/ftl/extension/firefox/ModClick;"

internal const val MENU_PACKAGE = "Lorg/mozilla/fenix/components/menu/"
internal const val COMPOSE_PACKAGE = "Lorg/mozilla/fenix/components/menu/compose/"

internal const val MAIN_MENU_KT = "${COMPOSE_PACKAGE}MainMenuKt;"
internal const val MENU_ITEM_KT = "${COMPOSE_PACKAGE}MenuItemKt;"
internal const val MENU_GROUP_KT = "${COMPOSE_PACKAGE}MenuGroupKt;"
internal const val MENU_NAVIGATION_KT = "${COMPOSE_PACKAGE}MenuNavigationKt;"
internal const val MENU_SCAFFOLD_KT = "${COMPOSE_PACKAGE}MenuScaffoldKt;"
internal const val EXTENSIONS_MENU_ITEM_KT = "${COMPOSE_PACKAGE}ExtensionsMenuItemKt;"
internal const val IP_PROTECTION_KT = "${COMPOSE_PACKAGE}IPProtectionMenuItemKt;"
internal const val ACCOUNT_KT = "${COMPOSE_PACKAGE}header/MozillaAccountMenuItemKt;"
internal const val BOTTOM_SHEET_HANDLE_KT = "Lmozilla/components/compose/base/BottomSheetHandleKt;"

internal const val COLOR_SCHEME = "Landroidx/compose/material3/ColorScheme;"
