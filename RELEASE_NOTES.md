Download the .apk below on your phone and open it to install or update (or in the app: Settings → App updates → Update app). It installs over 4.0.x (and 3.2) and keeps your settings and chats.

## What's new in 4.1.1 (test build, with NAS agent 1.5.4)
- **Cut-off answers say so:** an answer that reaches the length limit, or that the Tiiny stops sending early, ends with a note ("go on" gets the rest) instead of stopping mid-sentence.
- **NAS agent 1.5.4:** when the Tiiny is busy with a long answer for the phone and gives up waiting (HTTP 502/503/504), the agent tries again after 5, 10 and 20 s; if it still fails, the result shows what the Tiiny said.

## What's new in 4.1.0
Everything from the 4.0.7–4.0.24 test builds, tested on the phone, plus a final review.

**Gallery and results**
- Delete an image in the full view (asks first). Long-press to mark several, tap more, then Share or Delete them together; Back leaves marking.
- **Send to chat** from a result or the full view: the image or text arrives in the chat as an attachment. Images also have **Edit**. In the full view both sit at the top; a faint magnifier in the corner hints at zooming.

**Chat**
- ＋ → **Image from the Gallery**. An image for a model that can't see images is pointed out, and Send waits for a model with 👁.
- **Copies kept on the Tiiny** (🕘 menu): the Tiiny keeps a copy of every chat from every app, even incognito ones, by day. Delete today's or all, after a warning (whole days, can't be undone).
- **Enter sends** also through keyboard apps that turn a hardware Enter into a line break.
- **Faster follow-up answers:** the thinking depth goes with the question, so the Tiiny's prompt cache keeps working.

**Create**
- **Image → Characters:** describe someone once; tick them and they look the same in every image (same words and seed).
- **Image → Advanced → Live preview** (off by default): watch the image form, every 4 steps; makes images slower.
- **Songs with SongGeneration are complete** over Wi-Fi/WireGuard: exactly the chosen length, separate vocal and backing tracks as extra files (before, shorter songs were the cut-off opening of a long one).
- **Style chips** show ✓ when their word is in the text; tapping again takes it out.
- **Transcribe** reads WAV at any rate, stereo or 24/32-bit, and FLAC, Ogg and Opus.

**Automate and NAS agent 1.5.3** (update on the NAS: `cd ~/tiiny-agent && sudo docker compose pull && sudo docker compose up -d`)
- NAS runs show "Running on the NAS…" and their result appears by itself; ↻ fetches NAS results.
- News: when SearXNG finds little, the week's news, then Google News (and on the phone Bing News).
- webcal:// calendar links work; wrong links and ntfy addresses are pointed out; connector answers go away when you change a field.
- Agent tools alphabetical and named as in the editor.

**Everywhere**
- **Busy Tiiny:** "busy" answers (150004, "xh2a backend fail" after a load, 502 while another model loads) are tried again after 5, 10 and 20 s instead of failing.
- **Tables** with long texts show one block per row; <https://…> links lose their brackets.
- Shorter explanations in Settings, Chat and Create; the ? help covers the new things.

## Earlier in 4.0
4.0.6: NAS agent 1.5.0 (both apps on one NAS), Automate's Back, delete asks first, usual image shapes, tables, light-mode status bar. 4.0.5: several calendars for the NAS (agent 1.3), set-up guides for NAS, iCal and ntfy. 4.0.4: NAS agent 1.2 (the Tiiny reachable from the NAS), test answers under their button with a link to the log, lost-token step. 4.0.3: NAS agent 1.1 with your phone's time zone, one automation at a time. 4.0.2: voice via the model's own port, long recordings, song style references, safer attachments, Mode (Auto/Dark/Light), new pictograms. 4.0.1: the NAS agent's terminal guide. 4.0.0: Code, Automate, Translate, the redesigned Create and Gallery, chat attachments and dictation, incognito, NPU care, Speed statistics, guides. Details in those releases.
