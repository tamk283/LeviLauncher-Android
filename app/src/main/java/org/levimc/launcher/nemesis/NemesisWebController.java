package org.levimc.launcher.nemesis;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import org.levimc.launcher.core.auth.MsftAccountStore;
import org.levimc.launcher.core.minecraft.MinecraftLauncher;
import org.levimc.launcher.core.mods.inbuilt.InbuiltModuleProvider;
import org.levimc.launcher.core.mods.inbuilt.UnifiedMod;
import org.levimc.launcher.core.versions.GameVersion;
import org.levimc.launcher.core.versions.VersionManager;
import org.levimc.launcher.settings.FeatureSettings;
import org.levimc.launcher.ui.activities.MainActivity;
import org.levimc.launcher.ui.activities.MsftLoginActivity;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Only packaged HTML receives this bridge. No credentials or arbitrary file access cross it. */
public final class NemesisWebController {
    public static final String ORIGIN = "https://nemesis.local";
    private static final Set<String> IDS = new HashSet<>(Arrays.asList(
            "sprint", "zoom", "fps_display", "cps_display", "gyro", "camera_perspective", "toggle_hud"));
    private final Activity activity;
    private final boolean inGame;
    private final Runnable close;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final WebView web;
    private volatile boolean destroyed;
    private volatile VersionManager versions;
    private boolean launching;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    public NemesisWebController(Activity activity, boolean inGame, Runnable close) {
        this.activity = activity;
        this.inGame = inGame;
        this.close = close;
        web = new WebView(activity);
        web.setBackgroundColor(Color.rgb(13, 14, 18));
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        web.addJavascriptInterface(new Bridge(), "NemesisNative");
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // Native login is handled by MsftLoginActivity, never by a bridged external page.
                return !local(request.getUrl());
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (!local(uri)) return blocked();
                try {
                    String file = "/client.html".equals(uri.getPath()) ? "client.html" : "entry.html";
                    Map<String, String> headers = new HashMap<>();
                    headers.put("Cache-Control", "no-store");
                    headers.put("Content-Security-Policy", "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src data:; connect-src 'none'; frame-src 'none'; form-action 'none'; base-uri 'none'");
                    return new WebResourceResponse("text/html", "UTF-8", 200, "OK", headers,
                            activity.getAssets().open("nemesis/" + file));
                } catch (Exception error) { return blocked(); }
            }
            @Override public void onPageFinished(WebView view, String url) {
                if (local(Uri.parse(url))) refresh();
            }
        });
    }

    private static boolean local(Uri uri) {
        return "https".equals(uri.getScheme()) && "nemesis.local".equals(uri.getHost())
                && uri.getPort() == -1 && uri.getUserInfo() == null
                && ("/entry.html".equals(uri.getPath()) || "/client.html".equals(uri.getPath()));
    }
    private static WebResourceResponse blocked() {
        return new WebResourceResponse("text/plain", "UTF-8", 403, "Forbidden", new HashMap<>(),
                new ByteArrayInputStream(new byte[0]));
    }
    public WebView view() { return web; }
    public void load(boolean client) { web.loadUrl(ORIGIN + (client ? "/client.html" : "/entry.html")); }
    public void refresh() {
        if (destroyed || worker.isShutdown()) return;
        worker.execute(() -> {
            try {
                VersionManager manager = VersionManager.get(activity);
                manager.reload();
                versions = manager;
                MsftAccountStore.MsftAccount account = MsftAccountStore.getActive(activity);
                activity.runOnUiThread(() -> { if (!destroyed) sendState(manager, account); });
            } catch (Exception error) { activity.runOnUiThread(() -> error("Не удалось обновить список версий.")); }
        });
    }
    private String versionId(GameVersion version) {
        return (version.isInstalled ? "installed:" : "custom:") + version.directoryName;
    }
    private List<GameVersion> allVersions(VersionManager manager) {
        List<GameVersion> result = new ArrayList<>(manager.getInstalledVersions());
        result.addAll(manager.getCustomVersions());
        return result;
    }
    private static String nativeId(String id) { return "sprint".equals(id) ? "auto_sprint" : id; }
    private static String configKey(String id) {
        if ("zoom".equals(id)) return "zoom_level";
        if ("gyro".equals(id)) return "gyro_sensitivity_multiplier";
        return "overlay_size";
    }
    private UnifiedMod module(String id) {
        if (!IDS.contains(id)) return null;
        for (UnifiedMod mod : InbuiltModuleProvider.load(activity)) {
            if (nativeId(id).equals(mod.getId())) return mod;
        }
        return null;
    }
    private void sendState(VersionManager manager, MsftAccountStore.MsftAccount account) {
        try {
            JSONObject data = new JSONObject();
            data.put("inGame", inGame);
            data.put("account", account == null ? JSONObject.NULL : new JSONObject().put("name",
                    account.minecraftUsername != null ? account.minecraftUsername : account.xboxGamertag));
            JSONArray installed = new JSONArray();
            GameVersion selected = manager.getSelectedVersion();
            for (GameVersion version : allVersions(manager)) {
                installed.put(new JSONObject().put("id", versionId(version))
                        .put("name", String.valueOf(version.displayName) + " · " + String.valueOf(version.versionCode))
                        .put("selected", selected != null && versionId(selected).equals(versionId(version))));
            }
            data.put("versions", installed);
            JSONArray available = new JSONArray();
            List<UnifiedMod> nativeModules = InbuiltModuleProvider.load(activity);
            for (String id : IDS) {
                for (UnifiedMod mod : nativeModules) {
                    if (!nativeId(id).equals(mod.getId())) continue;
                    UnifiedMod.ConfigEntry config = mod.findConfigEntry(configKey(id));
                    available.put(new JSONObject().put("id", id).put("enabled", mod.isEnabled())
                            .put("value", config == null ? 56 : Integer.parseInt(config.currentValue)));
                }
            }
            data.put("modules", available);
            emit("update", data);
        } catch (Exception error) { error("Не удалось получить состояние модулей."); }
    }
    private void emit(String method, JSONObject data) {
        if (destroyed || activity.isFinishing() || activity.isDestroyed()) return;
        web.evaluateJavascript("window.NemesisHost&&window.NemesisHost." + method + "(" + data.toString() + ");", null);
    }
    private void error(String message) {
        if (destroyed) return;
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
        try { emit("error", new JSONObject().put("message", message)); } catch (Exception ignored) { }
    }

    public final class Bridge {
        @JavascriptInterface public void request(String method, String json) {
            if (method == null || method.length() > 32 || json == null || json.length() > 4096 || destroyed) return;
            activity.runOnUiThread(() -> {
                if (destroyed || web.getUrl() == null || !local(Uri.parse(web.getUrl()))) return;
                try { dispatch(method, new JSONObject(json)); }
                catch (Exception error) { error("Операция не выполнена. Проверь версию игры и настройки."); }
            });
        }
    }
    private void dispatch(String method, JSONObject input) throws Exception {
        switch (method) {
            case "refresh": refresh(); break;
            case "login":
                if (!inGame) activity.startActivityForResult(new Intent(activity, MsftLoginActivity.class), 0x4e45);
                break;
            case "manage":
                if (!inGame) activity.startActivity(new Intent(activity, MainActivity.class));
                break;
            case "client":
                if (!inGame) load(true);
                break;
            case "home":
                if (inGame) close.run(); else load(false);
                break;
            case "close": close.run(); break;
            case "selectVersion":
                if (inGame || versions == null) break;
                for (GameVersion version : allVersions(versions)) {
                    if (versionId(version).equals(input.optString("id"))) { versions.selectVersion(version); refresh(); break; }
                }
                break;
            case "launch": launchGame(); break;
            case "module": {
                String id = input.optString("id");
                UnifiedMod mod = module(id);
                if (mod == null) { error("Для этого модуля нет проверенного Android-бэкенда."); break; }
                if (input.has("enabled")) mod.applyEnabled(input.getBoolean("enabled"));
                if (input.has("value")) {
                    UnifiedMod.ConfigEntry config = mod.findConfigEntry(configKey(id));
                    if (config != null) {
                        int min = Integer.parseInt(config.minValue), max = Integer.parseInt(config.maxValue);
                        int value = Math.max(min, Math.min(max, input.getInt("value")));
                        mod.updateConfig(config, String.valueOf(value));
                    }
                }
                if (!input.has("value") && versions != null) sendState(versions, MsftAccountStore.getActive(activity));
                break;
            }
            default: break;
        }
    }
    private void launchGame() {
        if (inGame || launching) return;
        if (versions == null || versions.getSelectedVersion() == null) {
            error("Minecraft не найден. Открой управление версиями и импортируй свою копию игры.");
            return;
        }
        MsftAccountStore.MsftAccount active = MsftAccountStore.getActive(activity);
        if (FeatureSettings.getInstance().isLauncherManagedMcLoginEnabled()
                && (active == null || active.minecraftUsername == null || active.minecraftUsername.isEmpty())) {
            error("Сначала войди через Microsoft или используй штатный вход внутри Minecraft.");
            return;
        }
        launching = true;
        new MinecraftLauncher(activity).launch(new Intent(), versions.getSelectedVersion(), new MinecraftLauncher.LaunchCallback() {
            @Override public void onLaunchStarted() { launching = false; }
            @Override public void onLaunchFailed(Exception error) {
                launching = false;
                error("Не удалось запустить игру: " + String.valueOf(error.getMessage()));
            }
        });
    }
    public void destroy() {
        destroyed = true;
        worker.shutdownNow();
        web.stopLoading();
        web.removeJavascriptInterface("NemesisNative");
        web.destroy();
    }
}
