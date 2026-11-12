# Runtime diagnostics for 1.26.60.4

This is a read-only diagnostic addition, not an ESP implementation.

- In-game N menu > Диагностика 1.26.60.4 > Проверить.
- Finds a separately mapped libminecraftpe.so in /proc/self/maps. APK-backed mappings without a separate library path are reported as undetermined, never compatible.
- Reads only the mapped file, checks ELF64/AArch64, GNU Build ID and full SHA-256 on a worker thread.
- Does not read /proc/self/mem, change executable memory, install hooks, send network requests or expose filesystem paths/account credentials to HTML.
- Exact file match does not prove in-memory integrity, Mojang signature authenticity, live object layouts or hook compatibility. Files can differ from their live mappings.
- No candidate reverse-engineered address is installed. ESP remains disabled even on a file match.
- Destroying the host interrupts hashing and suppresses callbacks.

Expected Build ID: eb291b883dfdfaff70c4999c2eabd61885ae3e15
Expected library SHA-256: 4e7f97ecf1989edfaa7ca4805acc907a63bd4c56f1b1965079b2c47f63e3a7c4

Physical Android testing is still required. Launcher sign-in, launch and existing modules are unchanged.
