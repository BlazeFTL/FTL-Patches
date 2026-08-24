package app.ftl.patches.calendar

import app.ftl.util.returnEarly
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

val unlockPremiumPatch = bytecodePatch(
    name = "Unlock premium",
    description = "Unlocks premium features and removes ads.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_CALENDAR_PLANNER)

    execute {
        // isPremiumUser() drives every ad-container hide and premium-feature
        // gate app-wide, so forcing it true alone unlocks premium and removes
        // ads with a single edit.
        IsPremiumUserFingerprint.method.returnEarly(true)

        // Reference mod returns the literal "no_ads" instead of the real
        // stored subscription SKU string.
        GetActiveSubscriptionFingerprint.method.addInstructions(
            0,
            """
                const-string v0, "no_ads"
                return-object v0
            """.trimIndent(),
        )

        // onResume() already hides llAdContainer once isPremiumUser() is true.
        // Reference mod additionally hides the go-premium promo button
        // (clPremium) right after it - replicate that here too.
        val setVisibilityIndex = SettingActivityOnResumeFingerprint.instructionMatches.last().index

        SettingActivityOnResumeFingerprint.method.addInstructions(
            setVisibilityIndex + 1,
            """
                invoke-virtual {p0}, Lcalendar/agenda/schedule/event/advance/calendar/planner/BaseAct;->getBinding()Landroidx/viewbinding/ViewBinding;
                move-result-object v0
                check-cast v0, Lcalendar/agenda/schedule/event/advance/calendar/planner/databinding/ActivitySettingBinding;
                iget-object v0, v0, Lcalendar/agenda/schedule/event/advance/calendar/planner/databinding/ActivitySettingBinding;->clPremium:Landroidx/constraintlayout/widget/ConstraintLayout;
                const/16 v1, 0x8
                invoke-virtual {v0, v1}, Landroid/view/View;->setVisibility(I)V
            """.trimIndent(),
        )
    }
}
