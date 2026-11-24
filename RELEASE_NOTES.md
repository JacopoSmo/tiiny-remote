Download the .apk below on your phone and open it to install or update (or in the app: Settings → App updates → Update app). It installs over 4.0.x (and 3.2) and keeps your settings and chats.

## What's new in 4.0.19 (test build)
- **Live preview** fixed: if the Tiiny's live route refuses, the image is made the normal way instead of failing. The switch now sits at the end of Advanced.
- **Full view:** "Send to chat" and "Edit this" have room and stay clear of the phone's buttons.
- **? help** on Chat, Create and Gallery covers the new things; shorter texts for characters and live preview.

## What's new in 4.0.18 (test build)
- **Gallery:** delete an image in the full view (🗑 beside Share, asks first). Long-press images to mark several, then Share or Delete them (All marks everything).
- **Send to chat:** results and the full view have "Send to chat": the image or text goes into the chat as an attachment. Images also have "Edit this".
- **Chat → 📎 → Image from the Gallery:** pick one of your made images to send with a message.
- **Copies kept on the Tiiny** (chat history menu): lists the days the Tiiny keeps a copy of chats in its vault and deletes today's or all of them. It warns first: a whole day goes, from every app, with the memory notes taken from it; can't be undone.
- **Create → Image → Characters:** describe a person, animal or object once (name, looks, seed); tick it and it's drawn with the same words and seed in every image, so it looks the same from image to image.
- **Create → Image → Advanced → Live preview** (off by default): shows the image as it forms, every 4 steps. It makes images slower (each preview costs the Tiiny about 2.5 s per megapixel).
- **Songs (SongGeneration) of any length are complete:** over Wi-Fi/WireGuard the app now asks the model directly, which composes a whole song of exactly the chosen length (before, shorter songs were the opening of a full-length arrangement that just stopped). The separate tracks (vocals, backing) come as extra files. Over USB it works as before.

## What's new in 4.0.11 (test build)
Found in Tiiny Studio's API notes and on TiinyVerse:
- **Busy Tiiny:** when the Tiiny answers "busy" (error 150004: it's already working, for another app say; "xh2a backend fail" right after a load; 502 while another model loads), the app waits and tries again (after 5, 10, 20 s) instead of failing.
- **Faster follow-up answers:** the Tiiny keeps one prompt cache per model, keyed on the exact start of the chat. The thinking depth chosen per question now goes with the question instead of into the system prompt, so the earlier chat isn't read again for every answer.
- **FLAC, Ogg and Opus files** are decoded on the phone before transcribing (the Tiiny never answers FLAC).
- **Incognito** says that the Tiiny itself still keeps a copy of every chat in its vault.

## What's new in 4.0.10 (test build)
- **Enter sends, also through the keyboard app:** some keyboard apps take a hardware Enter themselves and type a line break, so the message didn't go out. A line break typed at the end now sends too (Shift+Enter still makes a new line).

## What's new in 4.0.9 (test build, with NAS agent 1.5.2)
- **Transcribe:** WAV files at other rates, in stereo or 24/32-bit (a voice clip made here, say) are converted to what the Tiiny's speech-to-text reads (16 kHz mono); before, they came back as an empty transcript. An empty result now says why instead of showing an empty card.
- **Calendar links:** webcal:// links (Apple, Outlook) work: they're read over https. A link that doesn't start with https:// or webcal:// and an ntfy address without https:// are pointed out under their fields.

## What's new in 4.0.8 (test build)
- **Connectors:** an answer under Save or Test goes away as soon as you change one of that card's fields (it was about the old values); spaces in the IMAP server address are ignored.

## What's new in 4.0.7 (test build, with NAS agent 1.5.1)
- **NAS runs show while they work:** after ▶ on a NAS automation, its card says "Running on the NAS…" and the result appears under Results by itself when it's done. ↻ at the top also fetches the NAS results.
- **News on the NAS and the phone:** when SearXNG has only a few news items for a topic, the week's news are tried, then Google News (and on the phone Bing News), instead of returning 2 stray items.
- **Tables with long texts** show as one small block per row ("Column: value"); links written as <https://…> lose their brackets.
- **Agent tools** are alphabetical and named as in the editor (Calendar, Email, News, Open pages, Web search).

## What's new in 4.0.6
- **NAS agent 1.5.0** (image `ghcr.io/jacoposmo/tiiny-remote-agent:latest`; update it on the NAS: `cd ~/tiiny-agent && sudo docker compose pull && sudo docker compose up -d`):
  - **Tiiny Remote and Tiiny Remote Exp on the same NAS** keep their own automations, connectors (calendars, email, push) and results; saving in one no longer replaces the other's. Your earlier setup counts as the normal app's. Older agents keep working with this app.
  - **Automation results** end without questions or offers of further help.
- **Automate:**
  - **Back means back:** going back (← or the back gesture) to Automate, e.g. from the log opened by a test answer, returns to the same section at the same place on the page. Opening Automate from the side menu starts on its list.
  - **Deleting an automation asks first.**
  - **Time:** emptying the time field to retype it no longer switches "At a set time" off; "7:00" and "0700" work too.
  - **Connectors:** Save answers right under its button ("Saved and sent to the NAS agent", or why not); email errors say plainly what's wrong (server not found, no answer, user or password refused); pasting more than 10 calendar links says how many were left out; ✕ on the last link empties it.
  - The examples are alphabetical.
- **Tables** in answers and results show as aligned columns instead of raw | lines.
- **Image shapes:** only the usual ones (1:1, 4:3, 3:2, 16:10, 16:9, 21:9 and upright); the style buttons are alphabetical.
- **Code:** the question field says what's missing (a project or a loaded model) instead of a silent grey Send; a question typed before the first project is kept; project names turn umlauts into plain letters ("über" → "ueber").
- **Light mode:** the phone's status bar icons turn dark, so they stay visible.

## Earlier in 4.0
4.0.5: several calendars for the NAS (agent 1.3), set-up guides for NAS, iCal and ntfy. 4.0.4: NAS agent 1.2 (the Tiiny reachable from the NAS), test answers under their button with a link to the log, lost-token step. 4.0.3: NAS agent 1.1 with your phone's time zone, one automation at a time. 4.0.2: voice via the model's own port, long recordings, song style references, safer attachments, Mode (Auto/Dark/Light), new pictograms. 4.0.1: the NAS agent's terminal guide. 4.0.0: Code, Automate, Translate, the redesigned Create and Gallery, chat attachments and dictation, incognito, NPU care, Speed statistics, guides. Details in those releases.
