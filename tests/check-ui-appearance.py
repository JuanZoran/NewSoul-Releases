#!/usr/bin/env python3
"""Catch new local surface implementations that bypass the shared style resolver.
Artwork/host injection exceptions are deliberately file+statement-specific.
"""
from pathlib import Path
import re
roots = [Path('app/src/main/java/dev/local/newsoul')]
allowed = {
    ('GamePicker.java', 'handle.setBackground(bg('),   # drag handle decoration
    ('GamePicker.java', 'tick.setBackground(bg('),     # selected status mark
    ('SettingsUi.java', 'container.setBackgroundColor('), # native Soul settings row
    ('SettingsUi.java', 'divider.setBackgroundColor('),
    ('SettingsUi.java', 'popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))'),
    ('AiReplyComponents.java', 'divider.setBackgroundColor('),
    ('GamePicker.java', 'window.setBackgroundDrawableResource(android.R.color.transparent)'),
    ('RecallMessageStyle.java', 'message.setBackgroundResource('), # host message bubble
    ('RecallMessageStyle.java', 'portrait.setBackground('), # native circular avatar outline
}
errors=[]
for root in roots:
    for path in root.glob('*.java'):
        if path.name in {'UiAppearance.java'}:
            continue
        for number,line in enumerate(path.read_text().splitlines(),1):
            if re.search(r'\.setBackground(?:Resource|Color|TintList|Drawable|DrawableResource)?\(',line):
                if not any(path.name==file and token in line for file,token in allowed):
                    errors.append(f'{path}:{number}: use UiAppearance role/bind for backgrounds')
            if re.search(r'\.set(?:Button|Thumb|Track)TintList\(',line):
                errors.append(f'{path}:{number}: use shared native-control appearance')
            if 'new GradientDrawable(' in line and path.name!='GamePicker.java':
                errors.append(f'{path}:{number}: local surface factory bypasses UiAppearance')
            if 'new GlassCardBackground(' in line:
                errors.append(f'{path}:{number}: resolve glass through UiAppearance')
assert not errors, '\n'.join(errors)
print('UI surface ownership audit passed (host artwork/status decorations explicitly excluded)')
