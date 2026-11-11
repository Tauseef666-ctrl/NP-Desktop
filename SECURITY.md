# Security Policy

<!--
  TODO: Replace the placeholder contact below (NEWPIPE_SECURITY_CONTACT) with a real
  email address once one is available for this project.
-->

## Supported Versions

| Version | Supported |
| --- | --- |
| Latest release of the v0.25.x series (currently v0.25.6) | Yes |
| Older releases | No — please upgrade to the latest release |

## Reporting a Vulnerability

Please report vulnerabilities **privately** — do not open a public GitHub issue with exploit details.

1. Open a [GitHub Security Advisory](https://github.com/Tauseef666-ctrl/NP-Desktop/security/advisories/new) (the preferred channel), **or**
2. Contact the maintainer privately via GitHub, or by email at `NEWPIPE_SECURITY_CONTACT` <!-- TODO: fill in a real contact address -->.

Please include:

- A description of the vulnerability and the affected component (desktop app, Android app, Android TV)
- Steps to reproduce or a proof of concept
- Potential impact
- A suggested fix, if you have one

You will receive an acknowledgement, and valid reports will be assessed and addressed. You will be kept informed of progress and credited in the fix or release notes if you wish.

## Scope

**In scope:**

- Code in this repository (desktop app, Android app, Android TV app, shared modules)
- Extraction and network behavior of the app itself (how it fetches content, handles responses, and stores data)
- Installer/packaging issues (e.g., unsigned binaries, bundled libraries)

**Out of scope:**

- Changes on the streaming platforms' side (e.g., YouTube changing endpoints or blocking traffic) — these affect all third-party clients and are not vulnerabilities in this app
- Social engineering, phishing, or attacks against the maintainer or users
- Denial-of-service against public platform endpoints
- Issues in upstream projects ([NewPipe](https://github.com/TeamNewPipe/NewPipe), [PipePipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor)) — please report those upstream, though we are happy to coordinate

## Disclosure Timeline

1. **Report received** — acknowledged promptly.
2. **Assessment** — severity and impact evaluated; you may be asked for clarification.
3. **Fix development** — a patch is prepared. The reporter is not asked to stop work or withhold details; we aim to coordinate.
4. **Coordinated disclosure** — after a fix is available, the advisory is published and credit is given.

## Data Handling

For context when assessing reports:

- The app talks **only to the streaming platforms' public endpoints and CDN hosts** (for example `youtube.com` and `googlevideo.com`, plus the equivalent hosts for the other supported services) to fetch content.
- There are **no analytics or telemetry servers** — the app does not phone home.
- Downloads, watch history, subscriptions, and caches **stay on the user's machine**; nothing is uploaded to project-controlled servers.

See [PRIVACY.md](PRIVACY.md) for the full privacy statement.

## Upstream

This is a fork of [NewPipe](https://github.com/TeamNewPipe/NewPipe). If a vulnerability affects shared components, it may also need to be reported upstream.
