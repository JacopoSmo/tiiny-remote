Download the .apk below on your phone and open it to install or update (or in the app: Settings → App updates → Update app). It installs over 4.0.x (and 3.2) and keeps your settings and chats.

## What's new in 4.6.0 (NAS agent 1.7.0, unchanged)
Everything from the 4.5.1–4.5.13 test builds, plus another review (programmer, white-hat, attacker), fonts, pictograms and wording included, and a pass for leaner code.

**Fast helper**
- **▶ beside the helper menu** (Settings) loads it, ■ unloads it.
- **⚡ Helper in the chat's options:** on, the helper is used (and loaded if it isn't); off, it isn't used even when loaded. ⚡ shows among the signs beside the model when it's on.
- **Speed tests of several pairs:** Test models picks up to 3 helpers and up to 6 main models; Speed tests loads and tests every pair in turn (Test runs times each), in the background, and loads your models back at the end. ■ stops it. Each pair goes to 📊 Statistics. Always ready loads nothing during a test, so the numbers stay clean.

**Recommended models**
- **"👍 Recommended · ⓘ why these"** is now the first line of every model menu.
- **✦ Update with the Tiiny's AI** no longer breaks off after minutes of thinking: the answer is streamed, asked once more if the connection drops, and keeps going in the background. The log notes when it starts.
- Helpers ranked by: fits beside the main model, then speed (no thinking first); Qwen3-4B-Instruct first.
- The table shows each model's NPU share once, and its Close bar is slimmer.

**Faster**
- A project's search scores each piece once instead of at every comparison: noticeably quicker with many files.

## What's new in 4.5.0 (with NAS agent 1.7.0)
Everything from the 4.4.1–4.4.12 test builds, plus another review (programmer, white-hat, attacker) of all of it, fonts and pictograms included.

**Recommended models**
- **👍 in every model menu:** the recommended model for that task, with a few words why; one that isn't on the Tiiny is offered first, to download right there (asked once, with the size and the free space).
- **Models → 👍:** the whole set as a table, one row per task, ✓ on your Tiiny or ⤓ to download; Download all missing.
- **✦ Update with the Tiiny's AI:** the most capable chat model on your Tiiny reads the Store's list and decides anew, in the background, nothing from outside; Back to the built-in set undoes it.
- Settings → Display → Recommended models: shown or hidden.

**Chat**
- **Projects** (🕘 → ＋ New project): a name, files (up to 50) and chats; each question gets the matching pieces of the files, by meaning with the Tiiny's embedding model and reranker, else by words. The files stay on the phone; deleting removes only the project's copies.
- **Several files per message** (up to five), each with its own ✕.

**Create**
- **Quick edits** (Image → Edit): Extend the picture, Remove an object, Remove the background, Zoom into a part, each with the FLUX.2-klein model made for it; a missing one downloads on the spot.
- **Improve prompt** uses Qwen-Image's own prompt enhancer (PE-T2I, PE-I2I) when it's there; with Qwen-Image chosen and none yet, ✦ offers to download it.
- **Voice:** dialogues with up to 4 voices (VibeVoice-1.5B); changing a recording's voice (CosyVoice; how the Tiiny wants it is still unconfirmed, the log shows its answer).
- **Transcribe:** who said what, with times (MOSS-Transcribe-Diarize).

**Models**
- **Store search** by name, maker, type or ability ("tts", "ocr", "0.6B"), no longer by words buried in descriptions.
- The Store's list goes into the log once per start (and when it changes).
- **Always ready** also keeps the embedding model, the reranker and a voice for reading aloud loaded.

**Fixes**
- **Choosing images and files works again** everywhere (since the app lock, every picker failed or closed the app).
- If the app ever crashes, the reason is in the log at the next start.

## What's new in 4.4.0 (with NAS agent 1.7.0)
Everything from the 4.3.1–4.3.13 test builds, tested on the phone, plus a full review (programmer, white-hat, attacker) of the app, the NAS agent and the link between them, with every font and pictogram checked.

**Look (Settings → Display)**
- **Style:** colours from your wallpaper (the standard) or a designed look: A · Ink & Paper, B · Graphite & Brass, C · Alpine Slate, each light and dark.
- **Pictograms:** colourful emoji, line icons in colour, or line icons in the style's colour. With line icons, every pictogram in the app is one thin-line family: the bin, share, back, history, microphone, the buttons' signs (⟳, ✎, ⤓, ⏸, ■…), the bottom bar.
- **Typefaces:** Phone's font, Designed pair (titles in the style's face, text in Inter), or Inter everywhere.
- **One size scale everywhere**, five levels: page titles, section headings, card titles, body text, notes and captions. Every text sits on one of them.
- **Status bar:** standard or quiet (one thin line).

**Privacy and backup (Settings)**
- **App lock:** fingerprint, face or the screen lock, at once or after 1, 5 or 15 minutes away. With it on, the recent-apps view shows no picture of the app.
- **Backup to the NAS:** everything of the app's, encrypted on the phone with your backup password (Show / Hide while typing); the NAS can't read it; the newest five kept; ⟳ reloads the list. Restore checks the whole backup first (a cut-off one is refused), then puts it in place as the app closes.
- **Encrypted link to the NAS agent:** the app pins the agent's own certificate the first time; Test shows its fingerprint, and the agent's log shows the same. If an agent that offers encryption answers without it, Test says so and tries encrypted again.
- **Find on my Wi-Fi** never sends your API key to an unknown device: a device that wants a key is only offered as "maybe the Tiiny".

**Easier**
- **Start page:** tasks instead of models. Cards in a row line up; **long-press a card and drag it** anywhere ("Original order" undoes it).
- **Undo instead of "Are you sure?"** in the Gallery, the full view and the chat list; it stays above the keyboard.
- **From outside the app:** the icon's long-press menu, a home-screen widget, Quick Settings tiles "Ask Tiiny" and "Translate copied".
- **Copies on the Tiiny (🗃)** in Chat, Create and the Gallery, just left of ⟳: the texts the Tiiny keeps, by day, to delete.
- **Settings grouped:** everything about the Tiiny together (🔎 left of the address).

**Models**
- **Updates work:** each card's Update and ⟳ Update models (all waiting updates in one list) start the Tiiny's update; the card shows "Updating X%". The Tiiny removes the old copy when an update begins, so ✕ on an update offers Pause; stopping leaves no copy.
- While an image is being made: ✕ and a Preview switch at the top right of the progress card.

**NAS agent 1.7.0:** the encrypted link and the backups; an email with an odd date or a calendar entry with an odd time no longer stops the whole summary; cut-off backup uploads are cleaned up; the log shows the certificate's fingerprint. Update it on the NAS: `cd ~/tiiny-agent && sudo docker compose pull && sudo docker compose up -d`.

Help (?) on every page and the welcome tour cover all of it.

## What's new in 4.3.0 (with NAS agent 1.6.0)
Everything from the 4.2.1–4.2.4 test builds, tested on the phone, plus another review (programmer, white-hat, attacker) of the changes and of updates, page reading, sharing, notifications and unlocking.
- **Full screens fit the screen:** the full view, the 3D viewer and Code's preview no longer run off the bottom.
- **Full view of a 3D model:** 🧊 View in 3D at the top beside Share; tapping the picture opens the viewer too.
- **No USB promises:** the Tiiny's USB link is made for computers (tested: the phone gets no connection on the "To PC" port), so the texts no longer mention it. Wi-Fi at home, WireGuard away.
- NAS agent: unchanged at 1.6.0.

## What's new in 4.2.0 (with NAS agent 1.6.0)
Everything from the 4.1.1–4.1.23 test builds, tested on the phone, plus a full review (as programmer, white-hat and attacker) of the app and the NAS agent.

**Gallery and results**
- **Swipe** left or right in the full view to the next or previous result, in the Gallery's order (a result with several images turns its pages first).
- **Tiles** for images, **videos (🎬, a frame from the clip)** and **3D models (🧊)**; texts and sound stay cards.
- **3D models open in the app:** its own viewer (turn with a finger, pinch to zoom; nothing is loaded from the internet). "Other app" still hands the file on. The tile shows the model itself once viewed.
- Several at once: long-press to mark, then share or delete; delete always asks first.
- Send to chat from a result; descriptions and OCR text show formatted.

**Pictograms**
- One set across the app: the help (?), hints and the legend now show exactly the pictograms on the buttons (history, save, microphone, share, delete…), and each page's help starts with its sidebar pictogram.
- 👂 for speech-to-text (ASR) everywhere; a drawn OCR pictogram for reading text from images.
- Models → Types: pictogram and name for every type, whatever the Labels setting.
- Settings → Labels → ⓘ lists every pictogram, the model types included.

**Models**
- **🧭 Help me choose** (top right): Chat opens with "Which model … fits best for this:"; the chat model looks through your Tiiny and the Store (new tool find_models) and suggests one or two.
- Making room on the NPU: the app picks what to unload itself (the smallest loaded model that frees enough, otherwise the fewest), named in one question.

**Chat**
- Chats (history) open as a small window: rename and delete (tap the red bin twice) react reliably.
- Cut-off answers say so ("go on" gets the rest); long files are condensed only when really long.
- Images from the Gallery; a warning when the model can't see images.

**Create**
- Characters (same person in every image), live preview ("Step 12 of 40"), Song → Separate tracks (vocals and backing), "From your audio" as a voice clip or style reference, Loop → Key back on screen.

**Code**
- ⋮ (project actions) and ▶ Preview right after the project; the preview fills the screen, isn't cut off, and can't show any file of the phone's outside the project.
- Enter sends, as in Chat.

**Status bar and Settings**
- Save no longer flashes the status bar red: it stays green unless the Tiiny's address or key changed; while checking it's amber ("One moment…").

**Automate and NAS agent 1.6.0**
- ntfy pushes in plain text, a test push from the app (🔌), a topic check, and a note in the agent's log for each push.
- The agent retries when the Tiiny is busy (HTTP 502/503/504, code 150004, recognised as the app does).
- Safer: a web page whose name switches to a home-network address after the check (DNS rebinding) is refused; calendar links must be web addresses (never a file on the NAS); connections that send nothing are dropped after 30 s.
- **Update the agent** to get 1.6.0: on the NAS (SSH), `cd ~/tiiny-agent && sudo docker compose pull && sudo docker compose up -d` (also in Set up NAS, step 5).

**Under the hood**
- File names from the Tiiny are cleaned before saving; a 3D model's picture is size-checked; deprecated icons replaced.

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
