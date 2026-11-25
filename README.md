# ha-phone

A **local smart-home hub that runs on an old Android phone**. When the phone joins a Wi-Fi network it discovers devices on that LAN and lets you control them. There is **no account, no login, no cloud, and no Home Assistant Core/OS**.

The on-phone UI is built to look like Home Assistant (sidebar, Lovelace-style cards, more-info sheet, HA blue `#03A9F4`). Other devices on the same network can open the hub in a browser at **port 8123**.

This project is **not affiliated with the Home Assistant project**. Home Assistant® and its logo are trademarks. See [Logos](#logos-and-trademarks) and [NOTICE](NOTICE).

## Install (sideload)

1. Download the APK from [GitHub Actions artifacts](../../actions) or a [GitHub Release](../../releases).
2. On the phone: **Settings → Security → unknown sources / Install unknown apps** and allow your file manager or browser.
3. Open the APK and install. Android 5.0 (API 21) or newer is enough.
4. Open **ha-phone**. Grant **location** (needed on many phones to see Wi-Fi / mDNS) and **notifications**. Allow the battery-optimization exemption so the hub stays alive.
5. Keep the phone on the same Wi-Fi as your lights, plugs, and speakers. Pull **Settings → Scan network**.

The hub runs as a **foreground service** (persistent notification). Optional **start on boot** is on by default.

### Old-phone notes

- **minSdk 21 (Android 5.0 Lollipop, 2014)** — as low as is practical for `NsdManager` (mDNS), multicast SSDP, foreground services, and a WebView that can still run this UI. API 19 would lose `NetworkCallback`-era APIs we do not need, but would also drop AndroidX/Material versions this project uses.
- Android 5–6 WebView is old Chromium. That is why we **do not ship the official Home Assistant frontend by default** (it requires BigInt / `<dialog>`, i.e. Chrome 67+). Our lookalike UI is ES5 and works on those WebViews.
- 32-bit ARM (`armeabi-v7a`) and 64-bit (`arm64-v8a`) are both included. No Python runtime, no VM, no Docker, no QEMU, no proot.

## What it controls

Local APIs only. Nothing here talks to vendor clouds to log you in.

| Kind | Discovery | Control | Notes |
| --- | --- | --- | --- |
| **WLED** | mDNS `_wled._tcp` + HTTP probe | On/off, brightness | `/json/state` |
| **Shelly** | mDNS `_http._tcp` probe | Relays / lights | Gen2 `/rpc` then Gen1 `/status` and `/relay/{id}?turn=` |
| **Tasmota** | HTTP probe | Power / dimmer | `/cm?cmnd=` |
| **Philips Hue** | mDNS `_hue._tcp` + SSDP | Lights | **Local pairing**: press the bridge button, then Pair. No Hue account. |
| **TP-Link Kasa** | UDP 9999 broadcast | Plug/bulb | Local XOR protocol |
| **LIFX** | UDP 56700 | On/off | LAN protocol |
| **Roku** | SSDP `roku:ecp` | Power / play | ECP port 8060 |
| **Sonos** | SSDP ZonePlayer | Play / pause / volume | UPnP SOAP |
| **ESPHome** | mDNS `_esphomelib._tcp` | Switch if `web_server` is on | Set entity path if needed |
| **DLNA / UPnP** | SSDP MediaRenderer | Play / pause / stop | |
| **Chromecast** | mDNS `_googlecast._tcp` | Discovery only | Full Cast needs Google Play Services; skipped so old/AOSP phones still work |
| **MQTT** | Manual broker + topics | Publish/subscribe light or switch | Settings save reconnects. Add device needs command/state topics |
| **Generic HTTP** | Manual | On/off/status URLs | Form collects URLs; missing URLs stay unavailable |

Add anything else by IP under **Devices → +**.

## Rooms, dashboard, scenes, automations

- **Overview** — area cards of entities (HA-style).
- **Areas** — rooms you name; assign devices when adding or from the device.
- **Scenes** — snapshot current on/off/brightness and restore later.
- **Automations** — **time of day** (`07:00`) or **state** (`switch.x` → `on`), then call `turn_on` / `turn_off` / `toggle` on another entity.
- State lives in a local Room database (survives restarts). **Settings → Backup** exports/imports JSON.

## LAN web UI

While the hub service is running, other devices can open:

```
http://PHONE_LAN_IP:8123/
```

Same UI as on the phone.

### Open LAN (default)

**There is no account.** With the default empty PIN, **anyone on the same Wi-Fi has full control**: read states, toggle devices, add/delete, restore backups. REST `/api/*` does not require a bearer token. The WebSocket at `/api/websocket` accepts any token (`auth_ok`) so a dropped-in official frontend can skip login. Responses send `Access-Control-Allow-Origin: *`, so a browser tab on any origin can call the API from this LAN.

This is intentional for a no-account local hub. Treat the phone like an unauthenticated Home Assistant on your LAN.

### Optional LAN PIN

Settings → **LAN PIN**. Off by default. When set:

- Requests from the phone itself (`127.0.0.1`) still work with no PIN.
- Other LAN clients must send `Authorization: Bearer <PIN>` (REST) or `{ "type": "auth", "access_token": "<PIN>" }` (WebSocket).
- CORS echoes the request `Origin` instead of `*`.
- The web UI shows a PIN screen if a call returns 401.

You can also turn off the LAN dashboard entirely (`web_dashboard` in settings / prefs) if you only want the on-phone UI.

## Architecture

```
Android foreground service
  ├─ Discovery (mDNS, SSDP, Kasa, LIFX, short HTTP unicast probe)
  ├─ Poll + device protocols (HTTP / UDP / SOAP)
  ├─ Automations
  ├─ Room DB (devices, entities, areas, scenes, automations)
  └─ HTTP + WebSocket on :8123
        ├─ HA-shaped REST (/api/states, /api/services/…)
        ├─ HA-shaped WS (get_states, call_service, lovelace/config, registries, …)
        └─ UI: assets/www (default) or assets/ha_frontend (optional)
```

The phone **is** the hub. Integrations run in-process. There is no Supervisor, no add-on store, no Home Assistant OS, no Docker.

## Why not bundle Home Assistant Core or the official frontend?

The original idea of embedding Home Assistant Core (Python) on Android without a VM was blocked by native wheels (`orjson` explicitly does not support Android, plus cryptography/ciso8601 version pins). You asked **not** to wrap HA OS in Docker/QEMU.

The **official Apache-2.0 frontend** (`home-assistant/frontend`) *can* be pointed at our compatibility API, but:

1. It needs a **modern WebView** (Chrome 67+: BigInt, `<dialog>`). Stock Android 5–7 often cannot run it.
2. It still expects a large surface of Core WS commands; we implement the dashboard subset (`get_states`, `subscribe_events` / `subscribe_entities`, `call_service`, `get_config`, `lovelace/config`, entity/device/area registries, stub translations/themes/user) and return empty success for unknown list/get commands.

So the **default APK** serves an original lookalike UI (HA colors, sidebar, cards, more-info) written in ES5. To try the official frontend on a new phone, copy a PyPI `hass_frontend` tree into `app/src/main/assets/ha_frontend/` (see that folder’s README) and rebuild.

## Logos and trademarks

**Not shipped:** the official Home Assistant wordmark/logo (trademark).

**Shipped:** a generic blue square + house (`brand_mark`). Swap these for personal sideloading if you have the right to use HA artwork:

| Asset | Path |
| --- | --- |
| Launcher / notification | `app/src/main/res/drawable/brand_mark.xml` |
| Adaptive icon (API 26+) | `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` |
| In-app / web UI logo | `app/src/main/assets/www/static/logo.svg` |
| Display name | `app/src/main/res/values/strings.xml` (`app_name`) |

Rebuild the APK after replacing files. Do not publish an APK with the HA logo unless you have permission.

## What this is not (HA OS / Supervisor)

| HA OS / Supervised feature | ha-phone |
| --- | --- |
| Add-ons (Mosquitto, ESPHome, Node-RED, …) | Not supported — no Supervisor, no containers |
| Supervisor / HassOS updates | Not supported |
| Home Assistant Cloud / Nabu Casa account | Not supported (and not desired) |
| Onboarding user creation | Stubbed: local owner, no password |
| Recorder history / energy dashboard / backups to HA cloud | Local JSON backup of hub config only |
| Bluetooth / Zigbee / Z-Wave radios | Not on the phone; use Wi-Fi devices or an MQTT dongle elsewhere |
| Official HA Core Python integrations (thousands) | Only the local protocols listed above |

## Build

```bash
./gradlew assembleRelease testDebugUnitTest
```

APKs:

- `app/build/outputs/apk/release/app-release.apk` (signed with `keystore/haphone.jks`, password `haphone`, alias `haphone` — sideload key, not a secrets-grade key)
- `app/build/outputs/apk/debug/app-debug.apk`

CI: `.github/workflows/build-apk.yml` builds both and attaches them to GitHub Releases when you push a `v*` tag.

## License

MIT for original ha-phone code (see [LICENSE](LICENSE)). Look-and-feel is inspired by the Apache-2.0 Home Assistant frontend; see [NOTICE](NOTICE). Device protocols are implementations of published local APIs (WLED, Shelly, Hue, Kasa, LIFX, UPnP, …).

## Honest test status

Verified in this repo’s environment when the APK job succeeds:

- Gradle **unit tests** for Kasa codec, LIFX header size, SSDP parse, name slug, Shelly gen1/gen2 JSON, generic HTTP URL rules, last_changed stamps.
- **APK build** (size reported on the PR / Actions log).

Not verified here (no Android emulator / no real IoT hardware in this environment):

- First-run permissions and battery dialog on a physical phone.
- mDNS/SSDP against real Hue / WLED / Shelly / Sonos / Roku hardware.
- WebView on Android 5 vs 14.
- Official `ha_frontend` drop-in.
- MQTT broker round-trip.
- Multi-day foreground-service survival on OEM killers (Xiaomi/Huawei may still need extra autostart toggles).
