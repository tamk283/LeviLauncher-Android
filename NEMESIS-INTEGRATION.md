# Nemesis Android integration

Two animated HTML assets are packaged offline under app/src/main/assets/nemesis.
SplashActivity opens NemesisActivity. Microsoft login delegates to the existing MsftLoginActivity. The HTML receives only a display name, never authentication credentials. Game launch uses the existing MinecraftLauncher, VersionManager and MinecraftLoadingActivity. The original MainActivity remains available for version importing and advanced settings.

MinecraftActivity attaches an in-activity N button. Its client menu binds to existing InbuiltModuleProvider/UnifiedMod modules: Auto Sprint, Zoom, FPS Display, CPS Display, Gyroscope, Camera Perspective and Toggle HUD. Toggles enable upstream overlay controls; the associated button may still need to be used in the game. The bridge does not claim automatic sprinting or gameplay effects just because an overlay is enabled.

ESP, Tracers, Name Tags, Fullbright, Aim Assist, Trigger and Step have no supported backend in this integration and are disabled. No third-party binary cheat payloads are downloaded. No version-specific entity offsets or hooks are guessed. Bedrock 26.53 compatibility has NOT been device-tested.

Security: only the two packaged HTTPS-origin assets can use the bridge. File/content access and external WebView navigation are blocked. Native actions and module identifiers are allowlisted. Imported UI profiles do not install native code. No new overlay permission is needed.

Sources inspected: https://github.com/LiteLDev/LeviLauncher-Android and https://github.com/LiteLDev/preloader-android. ApexAntLamina was inspected but not incorporated (its Android work is marked All Rights Reserved and no tested 26.53 ESP module was supplied).

Build: standalone-build GitHub Actions workflow, JDK 21, SDK 36, arm64-v8a. Existing upstream license/notice files retained.
