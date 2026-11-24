Download the .apk below on your phone and open it to install or update (or in the app: Settings → App updates → Update app). It installs over 4.0.x (and 3.2) and keeps your settings and chats.

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
