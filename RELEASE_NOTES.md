Download the .apk below on your phone and open it to install or update (or in the app: Settings → App updates → Update app). It installs over 4.0.x (and 3.2) and keeps your settings and chats.

## What's new in 4.0.3
- **NAS agent 1.1** (image `ghcr.io/jacoposmo/tiiny-remote-agent:latest`; update it on the NAS: `sudo docker compose pull && sudo docker compose up -d`):
  - **Your time zone:** the app now sends your phone's time zone, so "every day at 07:00" is 07:00 where you are. No `TZ` to set any more (it was Central European time for everyone).
  - **One at a time:** automations on the NAS run one after another: two at once share the Tiiny's speed, and a model load while another writes can upset the NPU.
  - **The right model:** without a chosen model, only a loaded chat model is used, never a speech-to-text or OCR model kept loaded by Always ready.
  - **Safer and sturdier:** search results, pages, calendar entries and emails are marked as data for every automation; malformed requests are refused; the image brings its own time-zone data.
- **Email pictogram:** a proper coloured envelope.

## Earlier in 4.0
4.0.2: voice via the model's own port, long recordings, song style references, safer attachments, Mode (Auto/Dark/Light), new pictograms. 4.0.1: the NAS agent's terminal guide. 4.0.0: Code, Automate, Translate, the redesigned Create and Gallery, chat attachments and dictation, incognito, NPU care, Speed statistics, guides. Details in those releases.
