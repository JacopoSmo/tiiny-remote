# Tiiny Remote 3.0

An Android app to run a Tiiny AI Pocket Lab from your phone, at home or over WireGuard. It talks to the Tiiny
the same way [Tiiny Studio](https://github.com/thezolon/tiiny-studio) does.

- **Models:** installed models with filters (Chat, Makes things, Sees images, Thinks), types, sorting, favourites,
  your own short names and "About this model". Load and unload, with a swap offer when the NPU is full.
- **Store:** browse TiinyOS's Model Store, download models (progress, pause, cancel), update models with a newer
  version, and delete models from the Tiiny.
- **Live strip:** online/offline, models loaded, NPU and CPU load (colour-coded), NPU memory and RAM in GB, power
  and temperature (when the Tiiny reports them); tap it for the full panel.
- **Chat:** several chats side by side, saved on the phone, with Thinking, "Can make things" (pictures, voice,
  music from the chat) and web search (your SearXNG, else Tavily, Bing, DuckDuckGo or Wikipedia from the phone).
- **Create:** images (create, edit, read text), voice (built-in, described or cloned), transcription, loops and
  songs, video, 3D models, text embedding, reranking and classification, laid out like Tiiny Studio. Results are
  kept under Creations and can be saved to the phone or shared.
- **Unlock:** after a restart the app unlocks the Tiiny with its password (or through the NAS over SSH).
- **VPN:** one-tap and automatic WireGuard switching (on away from home, off at home).
- **Activity:** one colour-coded log with filters and Share (secrets removed).
- **Display:** a compact layout for square screens (e.g. Unihertz Titan 2), sizes, and Enter-to-send for
  hardware keyboards.

## Install

Every push to `main` builds the app on GitHub. Open this repository's **Releases** on the phone, download the
newest `TiinyRemote-3.0.x.apk` and open it. Newer builds install over the old one and keep your settings.

Upgrading from 2.0.35 or older: uninstall the old app first (the signing key changed in 2.0.36).

## Set up

In **Settings**: the Tiiny's address (e.g. `http://192.168.x.x`), its API key (TiinyOS → Settings → API Key),
and the Tiiny's password for unlocking. Optional: the WireGuard tunnel name, your SearXNG address, a Tavily key,
and the NAS login for the SSH unlock fallback.

## Security notes

- Keys and passwords are stored encrypted with a key in the Android Keystore; app backups are off.
- The NAS's SSH key is saved at the first connection; a different key later is refused.
- Builds are signed with a key kept only in this repository's Actions secrets (`SIGNING_KEYSTORE_B64`,
  `SIGNING_PASSWORD`). Without them the build stops.
- Pages the chat opens can only be on the public internet, never on your home network, and only addresses from
  the chat's own search results or that you typed: a web page can't make the model send your chat elsewhere.
- Only web links (http/https) can be tapped in answers and sources.
- The shared log leaves out emails, keys, tokens, passwords, account names and the device's serial number, and
  never contains your content (chat answers, text read from documents, transcripts): only its size.
- The Tiiny only speaks plain HTTP, so use the app on your home network or through WireGuard.
- Unofficial: the Tiiny endpoints come from community notes and may change with firmware updates.
