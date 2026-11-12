"""Fail CI before Gradle if packaged UI/native integration is incomplete."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET
root = Path(__file__).resolve().parents[1]
manifest = (root/'app/src/main/AndroidManifest.xml').read_text()
ET.fromstring(manifest)
assert 'org.levimc.launcher.nemesis.NemesisActivity' in manifest
assert 'org.levimc.launcher.nemesis.NemesisActivity.class' in (root/'app/src/main/java/org/levimc/launcher/ui/activities/SplashActivity.java').read_text()
for name in ('entry', 'client'):
    text = (root/f'app/src/main/assets/nemesis/{name}.html').read_text()
    assert '<html' in text and '</html>' in text and 'NemesisNative.request' in text
    assert not re.search(r'<script[^>]+src=', text), 'UI must work without remote scripts'
    assert 'window.NemesisHost' in text
bridge = (root/'app/src/main/java/org/levimc/launcher/nemesis/NemesisWebController.java').read_text()
for required in ('setAllowFileAccess(false)', 'setAllowContentAccess(false)', 'MIXED_CONTENT_NEVER_ALLOW', 'local(Uri.parse(web.getUrl()))', 'MsftLoginActivity.class', 'MinecraftLauncher(activity)', 'InbuiltModuleProvider.load(activity)'):
    assert required in bridge, required
assert 'serializedAuthManager' not in bridge, 'Never send account credentials to the HTML layer'
assert 'NemesisGameOverlay(this)' in (root/'app/src/main/java/org/levimc/launcher/core/minecraft/MinecraftActivity.kt').read_text()
print('Nemesis integration checks passed: manifest, routing, assets, native launch/login, built-in modules and origin restrictions.')
