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

probe = (root/'app/src/main/java/org/levimc/launcher/nemesis/NemesisLibraryProbe.java').read_text()
assert '4e7f97ecf1989edfaa7ca4805acc907a63bd4c56f1b1965079b2c47f63e3a7c4' in probe
assert 'eb291b883dfdfaff70c4999c2eabd61885ae3e15' in probe
assert '/proc/self/mem' not in probe
assert '"diagnostics": runDiagnostics()' in bridge
assert '.put("hooksVerified", false).put("espReady", false)' in bridge
assert '"esp"' not in bridge.split('private static final Set<String> IDS', 1)[1].split('private final Activity', 1)[0]
client = (root/'app/src/main/assets/nemesis/client.html').read_text()
assert 'runtimeDiagnostics' in client and 'showDiagnostics' in client
print('Read-only runtime diagnostics and disabled ESP safeguards verified.')
