Download the .apk below on your phone and open it to install or update (or in the app: Settings → App updates → Update app). It installs over 4.0.x (and 3.2) and keeps your settings and chats.

## What's new in 4.2.2 (test build, with NAS agent 1.6.0)
- **Full view of a 3D model** (tested on 4.2.1: swiped to from another result, its button was cut off): 🧊 View in 3D sits at the top beside Share, and tapping the picture opens the viewer too.

## What's new in 4.2.1 (test build, with NAS agent 1.6.0)
- **Full screens no longer run off the bottom** (tested on 4.2.0: the 3D page's "View in 3D" was cut off): the full view, the 3D viewer and Code's preview now fit between the status bar and the phone's bottom edge.

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
