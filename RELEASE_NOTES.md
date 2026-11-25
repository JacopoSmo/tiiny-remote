Download the .apk below on your phone and open it to install or update (or in the app: Settings → App updates → Update app). It installs over 4.0.x (and 3.2) and keeps your settings and chats.

## What's new in 4.1.11 (test build, with NAS agent 1.5.6)
- **Pushes and notifications in plain text:** NAS pushes via ntfy (agent 1.5.6), phone notifications and the result cards' preview line show links as their words and lists with bullets, instead of raw ** and [text](link).
- **Code → ⋮ → Preview** opens again (it showed nothing): the page fills the screen, with Close at the top.
- **Code: Enter sends** like in Chat (Shift+Enter for a new line), following the same setting.
- **Making room on the NPU:** when a model doesn't fit, the app picks what to unload itself: the smallest loaded model that frees enough, otherwise the fewest models together, named in one question ("Unload A (29%) and B (4%)?"). Before, it followed the Tiiny's suggestion one model at a time, which was often too small, so models were unloaded for nothing (also when a Create job or a chat tool loads its model).
- **Choosing an image:** Describe and Video have the row "From your images" (one tap), like Edit and 3D; Translate's photo has "From the Gallery".
- **"From the Gallery"** beside "Choose an image" in Describe image, Edit image, Video's first frame, 3D and Translate's photo: your own made images, not only the phone's photos.
- **Descriptions and read text (OCR)** show formatted (bold, lists, code) instead of raw ** and ` signs, in the card and the full view.
- **Attached files:** without a helper model, the chat model only condenses really long files (over 24,000 characters) for follow-up questions. Before, it spent up to two minutes on an ordinary PDF and everything after it (a translation, say) waited. A condensed text that's barely shorter is not used.
- **Live preview** (tested: previews show while the image forms) says "Step 12 of 40" with a progress bar, and from the second image on shows previews up to the last step.
- **Set up ntfy:** step 3 says the test is typed in the NAS terminal (SSH app).
- **ntfy push:** an address without a topic is pointed out ("Add the topic at the end, e.g. …/tiiny"). Save says that results also come as pushes; Test (🔌) has the NAS send a test push, the same way results go out, and says whether ntfy took it. The agent's log notes each push.
- **Full view:** the zoom magnifier is larger, on a faint round backing, and clear of the phone's bottom bar.
- **Live preview:** the log notes what the image stream sent (to check the previews arrive).
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
