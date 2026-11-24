Download the .apk below on your phone and open it to install or update (or in the app: Settings → App updates → Update app). It installs over 4.0.x (and 3.2) and keeps your settings and chats.

## What's new in 4.0.4
- **NAS agent 1.2** (image `ghcr.io/jacoposmo/tiiny-remote-agent:latest`; update it on the NAS: `cd ~/tiiny-agent && sudo docker compose pull && sudo docker compose up -d`):
  - **The Tiiny reachable from the NAS:** the app now sends the Tiiny's address the way it uses it itself (http:// added, a trailing /v1 taken off). An address typed without http:// made the Tiiny "not reachable from the NAS". The agent also accepts such addresses now.
  - **Test says why:** if the NAS can't reach the Tiiny, Test tells the reason (timeout, refused key…).
- **Connector tests you can follow:** Test (NAS agent, email) shows "Testing…" and then its answer right below the button, green when it worked and red when not, with **Open the log (Automate) →**. Every NAS agent request and test is in the log.
- **Clearer messages:** a refused token says so plainly (also where Android reports it as an error of its own); spaces or line breaks copied with the address or token are ignored; an address without http:// is pointed out.
- **Lost the token?** The terminal guide's last step shows it again from the NAS.

## Earlier in 4.0
4.0.3: NAS agent 1.1 with your phone's time zone, one automation at a time. 4.0.2: voice via the model's own port, long recordings, song style references, safer attachments, Mode (Auto/Dark/Light), new pictograms. 4.0.1: the NAS agent's terminal guide. 4.0.0: Code, Automate, Translate, the redesigned Create and Gallery, chat attachments and dictation, incognito, NPU care, Speed statistics, guides. Details in those releases.
