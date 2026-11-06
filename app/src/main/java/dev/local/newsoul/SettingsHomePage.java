package dev.local.newsoul;



import static dev.local.newsoul.SettingsUi.*;

/** Category content; shared navigation, styling and controls remain in SettingsUi. */
final class SettingsHomePage {
    private SettingsHomePage() {}

    static void populate(MenuPanel panel) {
        AccountAutomationUi.addHide(panel);
        ProfileBookmarkControls.addSettings(panel);
        FollowDisguiseUi.addSettings(panel);

    }
}
