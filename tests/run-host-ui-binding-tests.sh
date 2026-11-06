#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
NEWSOUL_UI_ABI_TMP=$(mktemp -d "${TMPDIR:-/tmp}/newsoul-ui-abi.XXXXXXXX")
trap 'rm -rf -- "$NEWSOUL_UI_ABI_TMP"' EXIT
fixture="$NEWSOUL_UI_ABI_TMP/cn/soulapp/android/component/chat/fragment/BaseConversationFragment.java"
mkdir -p "$(dirname "$fixture")"
cat > "$fixture" <<'JAVA'
package cn.soulapp.android.component.chat.fragment;
import dev.local.newsoul.HostUiBindingsTest;
/** The exact legacy owner name is part of the host-version discriminator. */
public class BaseConversationFragment {
    private HostUiBindingsTest.Presenter x;
    private void onBubbleLongClick(HostUiBindingsTest.View view, HostUiBindingsTest.Message message, int index) {
        HostUiBindingsTest.hostInvoked();
    }
    private void onBubbleLongClick(HostUiBindingsTest.View view, Object message, int index) {
        HostUiBindingsTest.hostInvoked();
    }
}
JAVA
javac -encoding UTF-8 -source 17 -target 17 -d "$NEWSOUL_UI_ABI_TMP/classes" \
    app/src/main/java/dev/local/newsoul/HostBindings.java tests/HostUiBindingsTest.java "$fixture"
java -cp "$NEWSOUL_UI_ABI_TMP/classes" dev.local.newsoul.HostUiBindingsTest
