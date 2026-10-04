package app.ftl.patches.firefox

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import com.android.tools.smali.dexlib2.Opcode

private const val ACCESS_POINT = "Lorg/mozilla/fenix/components/menu/MenuAccessPoint;"
private const val COMPOSER = "Landroidx/compose/runtime/Composer;"
private const val OBJ = "Ljava/lang/Object;"
private const val MAIN_MENU_LAMBDA = "${COMPOSE_PACKAGE}MainMenuKt\$\$ExternalSyntheticLambda"

private val SURFACE_BRIGHT = fieldAccess(
    definingClass = COLOR_SCHEME,
    name = "surfaceBright",
    opcode = Opcode.IGET_WIDE,
)

private fun homeAccessPoint() = fieldAccess(
    definingClass = ACCESS_POINT,
    name = "Home",
    opcode = Opcode.SGET_OBJECT,
)

internal object BottomSheetHandleFingerprint : Fingerprint(
    definingClass = BOTTOM_SHEET_HANDLE_KT,
    custom = { method, _ -> method.name.startsWith("BottomSheetHandle") },
)

internal object ExtensionsMenuItemFingerprint : Fingerprint(
    definingClass = EXTENSIONS_MENU_ITEM_KT,
    name = "ExtensionsMenuItem",
    filters = listOf(moveStaticParam(5, 5)),
)

internal object WebExtensionMenuItemsFingerprint : Fingerprint(
    definingClass = EXTENSIONS_MENU_ITEM_KT,
    name = "WebExtensionMenuItems",
    filters = listOf(constTo(17, 0x40000000)),
)

internal object IPProtectionMenuItemFingerprint : Fingerprint(
    definingClass = IP_PROTECTION_KT,
    name = "IPProtectionMenuItem",
    filters = listOf(SURFACE_BRIGHT, constTo(14, 0x42500000)),
)

internal object IPProtectionBadgeFingerprint : Fingerprint(
    definingClass = "${COMPOSE_PACKAGE}IPProtectionMenuItemKt\$\$ExternalSyntheticLambda",
    name = "invoke",
    returnType = OBJ,
    parameters = listOf(OBJ, OBJ),
    filters = listOf(
        methodCall(definingClass = MENU_ITEM_KT, name = "Badge"),
        constTo(1, 0x41000000),
    ),
)

internal object MainMenuAddonsFingerprint : Fingerprint(
    definingClass = MAIN_MENU_KT,
    name = "Addons",
    filters = listOf(
        constTo(15, 0x40000000),
        homeAccessPoint(),
        methodCall(definingClass = "Ljava/util/Collection;", name = "isEmpty"),
        methodCall(definingClass = "Ljava/util/Collection;", name = "isEmpty"),
        methodCall(definingClass = "Ljava/util/Collection;", name = "isEmpty"),
        methodCall(definingClass = MAIN_MENU_KT, name = "MoreExtensionsMenuItem"),
    ),
)

internal object MainMenuLibraryGroupFingerprint : Fingerprint(
    definingClass = MAIN_MENU_KT,
    name = "LibraryMenuGroup",
)

internal object MainMenuFingerprint : Fingerprint(
    definingClass = MAIN_MENU_KT,
    name = "MainMenu",
    filters = listOf(
        constTo(1, 0x41000000),
        fieldAccess(
            definingClass = "Landroidx/compose/ui/Modifier\$Companion;",
            name = "\$\$INSTANCE",
            opcode = Opcode.SGET_OBJECT,
        ),
        opcode(Opcode.NEW_INSTANCE),
        methodCall(name = "rememberComposableLambda"),
        opcode(Opcode.NEW_INSTANCE),
        methodCall(name = "rememberComposableLambda"),
        opcode(Opcode.NEW_INSTANCE),
    ),
)

internal object MoreExtensionsMenuItemFingerprint : Fingerprint(
    definingClass = MAIN_MENU_KT,
    name = "MoreExtensionsMenuItem",
    filters = listOf(SURFACE_BRIGHT),
)

internal object MainMenuNavigationLambdaFingerprint : Fingerprint(
    definingClass = MAIN_MENU_LAMBDA,
    name = "invoke",
    returnType = OBJ,
    parameters = listOf(OBJ, OBJ),
    filters = listOf(
        homeAccessPoint(),
        opcode(Opcode.IF_EQ),
        methodCall(definingClass = MENU_NAVIGATION_KT, name = "MenuNavigation"),
        methodCall(name = "HorizontalDivider-9IZ8Weo"),
    ),
)

internal object MainMenuDividerLambdaFingerprint : Fingerprint(
    definingClass = MAIN_MENU_LAMBDA,
    name = "invoke",
    returnType = OBJ,
    parameters = listOf(OBJ, OBJ),
    filters = listOf(
        homeAccessPoint(),
        opcode(Opcode.IF_EQ),
        methodCall(name = "HorizontalDivider-9IZ8Weo"),
        methodCall(definingClass = MENU_NAVIGATION_KT, name = "MenuNavigation"),
    ),
)

internal object MainMenuColumnLambdaFingerprint : Fingerprint(
    definingClass = MAIN_MENU_LAMBDA,
    name = "invoke",
    returnType = OBJ,
    parameters = listOf(OBJ, OBJ, OBJ),
    filters = listOf(
        methodCall(definingClass = COMPOSER, name = "shouldExecute"),
        opcode(Opcode.MOVE_RESULT),
        opcode(Opcode.IF_EQZ),
        methodCall(definingClass = MENU_GROUP_KT, name = "MenuGroup"),
        methodCall(definingClass = MENU_GROUP_KT, name = "MenuGroup"),
        methodCall(definingClass = MENU_GROUP_KT, name = "MenuGroup"),
        fieldAccess(definingClass = ACCESS_POINT, name = "Browser", opcode = Opcode.SGET_OBJECT),
        methodCall(definingClass = MAIN_MENU_KT, name = "LibraryMenuGroup"),
        methodCall(definingClass = MENU_GROUP_KT, name = "MenuGroup"),
        methodCall(definingClass = MAIN_MENU_KT, name = "QuitMenuGroup"),
    ),
)

internal object MenuGroupFingerprint : Fingerprint(
    definingClass = MENU_GROUP_KT,
    name = "MenuGroup",
    filters = listOf(constTo(4, 0x40000000)),
)

internal object BadgeFingerprint : Fingerprint(
    definingClass = MENU_ITEM_KT,
    name = "Badge",
    filters = listOf(constTo(5, 0x41800000), constTo(6, 0x41000000)),
)

internal object MenuBadgeItemFingerprint : Fingerprint(
    definingClass = MENU_ITEM_KT,
    name = "MenuBadgeItem",
    filters = listOf(SURFACE_BRIGHT, constTo(12, 0x41000000), constTo(14, 0x41800000)),
)

internal object MenuItemFingerprint : Fingerprint(
    definingClass = MENU_ITEM_KT,
    name = "MenuItem",
    filters = listOf(
        moveObject(5, 15),
        SURFACE_BRIGHT,
        constTo(0, 0x42600000),
        constTo(0, 0x42500000),
    ),
)

internal object MenuTextItemFingerprint : Fingerprint(
    definingClass = MENU_ITEM_KT,
    name = "MenuTextItem",
    filters = listOf(
        constTo(2, 0x42600000),
        constTo(2, 0x42500000),
        SURFACE_BRIGHT,
    ),
)

internal object WebExtensionMenuItemFingerprint : Fingerprint(
    definingClass = MENU_ITEM_KT,
    name = "WebExtensionMenuItem-lVb_Clg",
    filters = listOf(
        SURFACE_BRIGHT,
        constTo(27, 0x1fe57c),
        constTo(8, 0),
    ),
)

internal object MenuItemIconLambdaFingerprint : Fingerprint(
    definingClass = "${COMPOSE_PACKAGE}MenuItemKt\$\$ExternalSyntheticLambda",
    name = "invoke",
    returnType = OBJ,
    parameters = listOf(OBJ, OBJ),
    filters = listOf(
        fieldAccess(opcode = Opcode.IGET_OBJECT, type = "Lkotlin/jvm/functions/Function0;"),
        opcode(Opcode.IF_EQZ),
        methodCall(definingClass = "Landroidx/compose/foundation/layout/SizeKt;", name = "size-3ABfNKs"),
    ),
)

internal object MenuNavItemFingerprint : Fingerprint(
    definingClass = MENU_NAVIGATION_KT,
    name = "MenuNavItem",
    filters = listOf(
        fieldAccess(
            definingClass = "Lmozilla/components/compose/base/theme/AcornTypographyKt;",
            name = "defaultTypography",
            opcode = Opcode.SGET_OBJECT,
        ),
        methodCall(definingClass = "Landroidx/compose/material3/TextKt;", name = "Text-Nvy7gAk"),
    ),
)

internal object MenuFrameFingerprint : Fingerprint(
    definingClass = MENU_SCAFFOLD_KT,
    name = "MenuFrame",
    filters = listOf(constTo(12, 0x41400000)),
)

internal object AccountMenuItemFingerprint : Fingerprint(
    definingClass = ACCOUNT_KT,
    name = "MozillaAccountMenuItem",
    filters = listOf(SURFACE_BRIGHT, constTo(12, 0x42600000)),
)

internal object AccountSubtitleLambdaFingerprint : Fingerprint(
    definingClass = "${COMPOSE_PACKAGE}header/MozillaAccountMenuItemKt\$\$ExternalSyntheticLambda",
    name = "invoke",
    returnType = OBJ,
    parameters = listOf(OBJ, OBJ),
    filters = listOf(
        methodCall(definingClass = "Landroidx/compose/material3/TextKt;", name = "Text-Nvy7gAk"),
        fieldAccess(opcode = Opcode.IGET_OBJECT, type = "Ljava/lang/String;"),
        opcode(Opcode.IF_NEZ),
    ),
)

private const val DIALOG_FRAGMENT = "${MENU_PACKAGE}MenuDialogFragment;"
private const val MENU_FRAGMENT = "${MENU_PACKAGE}MenuFragment;"
private const val DIALOG = "Landroid/app/Dialog;"
private const val BUNDLE = "Landroid/os/Bundle;"
private const val DIALOG_INTERFACE = "Landroid/content/DialogInterface;"
private const val INSETS_COMPAT = "Landroidx/core/view/WindowInsetsCompat;"

internal object DialogFragmentCreateDialogFingerprint : Fingerprint(
    definingClass = DIALOG_FRAGMENT,
    name = "onCreateDialog",
    returnType = DIALOG,
    parameters = listOf(BUNDLE),
)

internal object MenuFragmentCreateDialogFingerprint : Fingerprint(
    definingClass = MENU_FRAGMENT,
    name = "onCreateDialog",
    returnType = DIALOG,
    parameters = listOf(BUNDLE),
)

internal object DialogFragmentViewCreatedFingerprint : Fingerprint(
    definingClass = DIALOG_FRAGMENT,
    name = "onViewCreated",
    returnType = "V",
    parameters = listOf("Landroid/view/View;", BUNDLE),
    filters = listOf(methodCall(definingClass = OBJ, name = "getClass")),
)

internal object DialogFragmentWidthFingerprint : Fingerprint(
    definingClass = DIALOG_FRAGMENT,
    name = "calculateMenuSheetWidth",
    returnType = "I",
    parameters = listOf(),
)

internal object MenuFragmentWidthFingerprint : Fingerprint(
    definingClass = MENU_FRAGMENT,
    name = "calculateMenuSheetWidth\$1",
    returnType = "I",
    parameters = listOf(),
)

internal object DialogFragmentShowFingerprint : Fingerprint(
    definingClass = "${MENU_PACKAGE}MenuDialogFragment\$\$ExternalSyntheticLambda",
    name = "onShow",
    returnType = "V",
    parameters = listOf(DIALOG_INTERFACE),
)

internal object MenuFragmentShowFingerprint : Fingerprint(
    definingClass = "${MENU_PACKAGE}MenuFragment\$\$ExternalSyntheticLambda",
    name = "onShow",
    returnType = "V",
    parameters = listOf(DIALOG_INTERFACE),
)

internal object DialogFragmentInsetsFingerprint : Fingerprint(
    definingClass = "${MENU_PACKAGE}MenuDialogFragment\$\$ExternalSyntheticLambda",
    name = "onApplyWindowInsets",
    returnType = INSETS_COMPAT,
    parameters = listOf("Landroid/view/View;", INSETS_COMPAT),
)

internal object MenuFragmentInsetsFingerprint : Fingerprint(
    definingClass = "${MENU_PACKAGE}MenuFragment\$\$ExternalSyntheticLambda",
    name = "onApplyWindowInsets",
    returnType = INSETS_COMPAT,
    parameters = listOf("Landroid/view/View;", INSETS_COMPAT),
)
