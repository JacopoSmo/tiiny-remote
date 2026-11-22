# Tiiny Remote 3.2

An Android app to run a Tiiny AI Pocket Lab from your phone, at home or over WireGuard. It talks to the Tiiny
the same way [Tiiny Studio](https://github.com/thezolon/tiiny-studio) does.

- **Models:** installed models with filters (Chat, Makes things, Sees images, Thinks, Mixture of experts), types,
  sorting, favourites, your own short names and "About this model". Mixture-of-experts models (…-A3B, gpt-oss)
  are tagged "MoE". Load and unload, with a swap offer when the NPU is full.
- **Store:** browse TiinyOS's Model Store, download models (progress, pause, cancel), update models with a newer
  version, and delete models from the Tiiny.
- **Live strip:** online/offline, models loaded, helper on/off, NPU and CPU load (colour-coded), NPU memory and RAM
  in GB, power and temperature (when the Tiiny reports them); tap it for the full panel (main and helper model,
  what's working).
- **Fast helper model:** a quick chat model (e.g. gpt-oss-20b) kept loaded beside the main one names new chats while
  the main model answers, suggests follow-ups, summarises, translates and rewrites, condenses long chats, runs the
  writing helpers, and condenses web pages when it's at least 25% faster than the chat model (condensing can lose
  details). A speed test (Settings, 1–4 runs averaged) measures one model at a time against two at once.
- **Chat:** several chats side by side, saved on the phone, with a Thinking slider (Off, Auto, Low, Medium, High,
  Max), "Can make things" (pictures, voice, music from the chat), web search (your SearXNG, else Tavily, Bing,
  DuckDuckGo or Wikipedia from the phone), follow-up suggestions, Socratic and Student modes, Summarise, Translate
  an answer, ✎ to shorten or reword your message, and a jump-to-newest button.
- **Create:** images (create, edit, read text), voice (built-in, described or cloned), transcription, loops and
  songs, video, 3D models, text embedding, reranking and classification, laid out like Tiiny Studio. Results are
  kept under Creations and can be saved to the phone or shared.
- **Unlock:** after a restart the app unlocks the Tiiny with its password (or through the NAS over SSH).
- **Tiiny power:** Restart and Power off from Settings (to switch it on again, press the Tiiny's power button).
  NPU faults the Tiiny reports (e.g. a PCIe transfer timeout) are logged and shown in the status panel.
- **App updates:** Check for updates and Update app in Settings (the browser downloads the new version; open it to
  install), plus an optional daily check with a notification.
- **VPN:** one-tap and automatic WireGuard switching (on away from home, off at home).
- **Activity:** one colour-coded log with filters and Share (secrets and the phone's own address removed).
- **About:** Settings → ⓘ beside the version: version, links, what data goes where, licence.
- **Display:** a compact layout for square screens (e.g. Unihertz Titan 2), sizes (menus and dialogs included),
  labels as words, pictograms or both (with a legend), and Enter-to-send for hardware keyboards.
- **In the background (optional):** keeps working while a job, chat answer, speed test or download runs with the
  app in the background, and notifies you when it's done.

## Install

Every push to `main` builds the app on GitHub. Open this repository's **Releases** on the phone, download the
newest `TiinyRemote-3.2.x.apk` (the one marked Latest) and open it. Newer builds install over the old one and keep your settings.

Pre-releases named "Tiiny Remote Experimental" come from the `experimental` branch and install as a separate app,
"Tiiny Remote Exp", with its own settings; they're for trying new features and never marked Latest.

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
- The shared log leaves out emails, keys, tokens, passwords, account names, and what identifies the device (serial
  number, device ID, its name, MAC addresses), and
  never contains your content (chat answers, text read from documents, transcripts): only its size.
- "Done" notifications show only "Tiiny Remote: a job is done" on the lock screen; their text appears once unlocked.
- Cancelling a download asks first and never deletes an installed model.
- The Tiiny only speaks plain HTTP, so use the app on your home network or through WireGuard.
- Unofficial: the Tiiny endpoints come from community notes and may change with firmware updates.

## Licence

MIT, see [LICENSE](LICENSE): free to use, change and share, with no warranty. Not affiliated with Tiiny AI; built with Claude (Anthropic), supported by my home Tiiny AI.
