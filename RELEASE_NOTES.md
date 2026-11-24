Download the .apk below on your phone and open it to install or update (or in the app: Settings → App updates → Update app). It installs over 4.0.x (and 3.2) and keeps your settings and chats.

## What's new in 4.0.2
- **Voice that works with more models:** speech goes through the voice model's own port first (at home or over the VPN). Through TiinyOS's gateway, VoxCPM2, OmniVoice, MOSS-TTS-Nano, Qwen3-TTS CustomVoice and VibeVoice-Realtime often fail or drop the last sentence. "The model's own voice" lets models without built-in speakers (VoxCPM2) speak with no clip to clone.
- **Long recordings:** over 80 s they go in ~60 s parts cut at quiet moments (the Tiiny fails past ~90 s): long dictations, transcriptions and spoken translations now work.
- **Songs:** a style reference (a WAV, 10–30 s of music whose feel the song borrows) for SongGeneration and AceStep.
- **3D:** images with see-through parts are cleaned, cropped and centred first: no more flat cards or big floors.
- **Safer attachments:** a file's text goes to the model between markers it's told never to obey; a file that looks like it gives the model instructions still goes along as data, but the chat runs without tools and web search while it's there, and you're told why.
- **Model Store:** paused downloads show Resume, downloads stopped by an error show Retry, and a model being unloaded isn't taken for a download.
- **Improve prompt** uses a loaded Qwen-Image prompt enhancer (PE-T2I / PE-I2I) first.
- **Mode:** Settings → Display → Mode: Auto, Dark or Light.
- **Pictograms:** web search is a blue globe with a magnifier; Translate and Automate use their page pictograms everywhere; agents have a robot head, processes a numbered list.
- **SearXNG from the terminal:** Settings → Web search → How to set it up (Terminal); both terminal guides end with an optional clean-up of old Docker images.
- **Smaller:** the app's own examples everywhere (Video: "Describe the clip"); PP-DocLayoutV3 isn't offered for OCR; a Create job doesn't start with under 2 GB free on the Tiiny; and the clean-ups of a "Slytherin Mode" review (style references must be WAV, as the Tiiny requires; a suspicious-file warning only when it actually switches something off).

## New in 4.0 (in case you're coming from 3.2)
Code (projects with a coding model on your Tiiny, GitHub included), Automate (agents and processes, on the phone or 24/7 on a NAS), Translate (text, photo, speech), the redesigned Create and a new Gallery, chat attachments and dictation, incognito chats, NPU care, Speed statistics, a welcome tour and a ? on every page, sidebar order and ⏻ shut-down. Details in the 4.0.0 release.
