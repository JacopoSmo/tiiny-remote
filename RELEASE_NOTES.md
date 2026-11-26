Download the .apk below on your phone and open it to install or update (or in the app: Settings → App updates → Update app). It installs over 4.0.x (and 3.2) and keeps your settings and chats.

## What's new in 4.3.12 (test build)
- **Model updates:** stopping one now asks first and offers Pause. The Tiiny removes the old copy when an update begins, so stopping left no copy.
- **Backup list:** the refresh button showed a stray sign; it shows ⟳ now.

## What's new in 4.3.10 – 4.3.11 (test builds, with NAS agent 1.7.0)
- **One size scale everywhere**, five levels: page titles, section headings (Settings, Create), card titles, body text, notes and captions; headings a little larger than before.
- **Typefaces:** Phone's font, Designed pair, or Inter everywhere.
- **Model updates actually start:** the download route answers "already downloaded" for an installed model (tested on 4.3.9: nothing happened), so the likely update routes are tried in turn and the log names the one that worked. While it runs, the card says "Updating X%" with a bar, and ⟳ Update models shows the same.
- **🗃 Copies on the Tiiny** in the same place in Chat, Create and the Gallery: just left of ⟳ (in Chat after ＋); the chat list no longer has its own link.
- **Settings → Tiiny:** 🔎 sits left of the address, on the same line.
- **Undo** stays above the keyboard when it opens.
- **Backup:** sizes shown correctly (with one decimal; "0 MB" was read after the temporary file was gone), and the log lists what went in, per folder.

## What's new in 4.3.7 – 4.3.9 (test builds, with NAS agent 1.7.0)
- **Models → ⟳ Update models** (beside the orange ▶, with the number waiting): every model with an update in one list, each with its state (available, updating with %, paused, updated ✓); update one or all, pause, resume.
- **Pictograms:** three kinds: colourful emoji, line icons in colour, line icons in the style's colour (web search's sign too, which was always blue).
- **Undo after deleting** now shows (it vanished at once: tested on 4.3.6).
- **Backup password:** Show / Hide while typing.
- **Copies on the Tiiny (🗃) in Create and the Gallery:** the Tiiny keeps the texts sent from there (Describe, OCR, Improve prompt, lyrics) like chats, by day; images, sound and videos it doesn't keep.
- **Back from Start** (or the Gallery) into a Create tool returns there in one step (it went to Create Image first).
- **Find on my Wi-Fi:** tries the saved address first, asks a few addresses at a time (it choked the Wi-Fi, so the app thought you'd left home and switched the VPN on: tested on 4.3.6), asks with the API key where a Tiiny wants it, and logs what answered.
- **Settings grouped:** everything about the Tiiny together: address and key, Restart and Power off, unlocking after a restart with the NAS fallback right under it, then WireGuard.
- **While an image is being made:** ✕ (stop) at the top right of the progress card, with a Preview switch beside it: the preview can be shown or hidden any time.
- Help (?) and the welcome tour cover all of it.

## What's new in 4.3.1 – 4.3.6 (test builds, with NAS agent 1.7.0)

**Look (Settings → Display)**
- **Style:** colours from your wallpaper (the standard, as before) or a designed look: A · Ink & Paper, B · Graphite & Brass, C · Alpine Slate, each light and dark (Mode).
- **Pictograms:** colourful emoji (as before) or one family of line icons, drawn for this app.
- **Typefaces:** the phone's font, or a designed pair: Inter for reading, titles in the style's own face (Newsreader, Inter Tight, Manrope).
- **Status bar:** standard (the coloured block) or quiet: one thin line with a dot, the model and a slim NPU bar.

**Privacy and backup (Settings)**
- **App lock:** fingerprint, face or the screen lock to open the app, at once or after 1, 5 or 15 minutes away. Jobs and automations go on while it's locked.
- **Encrypted link to the NAS agent** (agent 1.7): the agent makes its own certificate; the app pins it the first time and refuses any other after that. Once the app has used it, the agent refuses unencrypted requests. Test in Connectors shows "Encrypted link: on"; "Forget certificate" is there for setting the agent up anew.
- **Backup to the NAS:** settings, chats, the Gallery, voices, Code projects and automations, encrypted on the phone with your backup password (the NAS can't read it), the newest five kept. Restore brings one back, also onto a new phone.

**Easier**
- **Start page:** what you can do, as tasks ("Translate", "Make a song", "Read text from a photo"…), each opening the right page and tool. First in the sidebar for new setups.
- **Find on my Wi-Fi** (Settings → Tiiny): finds the Tiiny's address by itself.
- **Undo instead of "Are you sure?":** deleting in the Gallery, the full view and the chat list happens at once; Undo at the bottom brings it back for a few seconds.
- **From outside the app:** long-press the icon (Dictate, Chat, Translate copied text, Gallery), a home-screen widget (Chat, Dictate, Translate), and Quick Settings tiles "Ask Tiiny" and "Translate copied".

**NAS agent 1.7.0:** the encrypted link and the backups. Update it on the NAS: `cd ~/tiiny-agent && sudo docker compose pull && sudo docker compose up -d`.

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
