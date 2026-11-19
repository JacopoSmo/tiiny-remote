# Tiiny Remote

A small Android app for a Tiiny AI Pocket Lab on your home network:

- **Models:** see which models are installed and loaded, and load or unload them. If the NPU is full,
  the app tells you which model the Tiiny suggests unloading and swaps them on request.
- **Unlock:** after a restart the Tiiny stays locked until someone logs in. The app connects to your
  NAS over SSH and runs `tiiny login` there (the Tiiny CLI must be installed on the NAS). With
  automatic unlock on, it does this by itself when it finds the Tiiny locked.

It talks to the Tiiny the same way [Tiiny Studio](https://github.com/thezolon/tiiny-studio) does,
over plain HTTP, so use it on home wifi or over WireGuard only.

## Install

Every push to `main` builds the app on GitHub. On your phone, open this repository's **Releases**,
download the newest `TiinyRemote-….apk` and open it. Android asks once to allow installs from your
browser. Newer builds install over the old one and keep your settings.

## Set up

In **Settings**:

| Field | Example |
| --- | --- |
| Tiiny address | `http://192.168.3.143` |
| API key | TiinyOS → Settings → API Key |
| NAS address | the NAS's IP |
| SSH port | `22` |
| NAS username / password | your local NAS admin account |

Tap **Save and test NAS login**; the Unlock tab shows whether SSH works and the Tiiny CLI was found.
SSH has to be switched on in UGOS (Control Panel → Terminal) for unlocking to work.

The unlock command runs `. ~/.profile; tiiny login` on the NAS. Change it under Settings if your CLI
lives elsewhere.

## Security notes

- The API key and NAS password are stored encrypted with a key in the Android Keystore.
- The NAS's SSH key is saved at the first connection; a different key later is refused.
- `app/release.keystore` (password `tiinyremote`) only signs this personal app so updates install
  over each other. It protects nothing else.
- Unofficial: the Tiiny endpoints come from community notes and may change with firmware updates.
  Settings → *Show last API answers* shows what the device returned.
