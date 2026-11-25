Optional official Home Assistant frontend (Apache-2.0)
======================================================

The app's default UI is an original lookalike that works on old Android
WebViews (API 21). The official Home Assistant frontend (home-assistant/frontend)
needs a modern Chromium (BigInt + dialog) and will not boot on stock Android 5–6
WebView.

If you still want to try the official frontend on a newer phone or desktop
browser on the LAN:

1. Download a home-assistant-frontend wheel from PyPI (Apache-2.0), e.g.
   home_assistant_frontend-20231208.2-py3-none-any.whl
2. Unzip it and copy the contents of hass_frontend/ into THIS directory
   so that index.html sits at:

     app/src/main/assets/ha_frontend/index.html

3. Rebuild the APK. The local server prefers ha_frontend/index.html when present.
4. Auth is stubbed: any access token is accepted and no account is created.

Keep hass_frontend's LICENSE/NOTICE next to those files. This does not grant
trademark rights to the Home Assistant name or logo.
