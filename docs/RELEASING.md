# Releasing and publishing OrionekoX

## 0. Versioning

Use `major.minor.patch` and do not only bump the last digit:

- **patch** (`x.y.Z`): bug fixes, QA passes, copy/docs only.
- **minor** (`x.Y.0`): a larger feature iteration or compatibility layer (new UI
  mode, import format, theme system, group/media behavior). Reset patch to 0.
- **major** (`X.0.0`): a milestone/architecture release that changes the product’s
  shape or compatibility baseline. Reset minor/patch to 0.
- `versionCode = APP_VERSION_CODE * 10 + 9` as required by the build.

If a round ships a feature, bump minor even if the previous round was a patch. If it
completes a milestone, bump major. Record the reason in the build report/memory.

This checklist applies to public pushes, GitHub releases and any text that becomes
publicly visible. The repository is GPL-2.0; keep upstream notices intact.

## 1. Sanitize

Never publish:

- keystores (`*.keystore`, `*.jks`, `*.p12`) or signing passwords **that belong to us**;
- **our own** `google-services.json` / Firebase API keys;
- private email addresses, phone numbers, internal IPs or home-directory paths;
- provider API keys, OAuth tokens, cookies or `~/.dsh` credentials;
- user data backups (`*.oxagent`, chat databases, agent workspaces);
- real Telegram display names or usernames in QA records — use `TestUser`.

⚠️ **Upstream test credentials are NOT leaks.** `TMessagesProj/google-services.json`
(`project_id: tmessages2`, package `org.telegram.messenger`) and
`TMessagesProj/config/release.keystore` are Telegram's own public test credentials,
byte-identical to `origin/master` (blobs `640e841b` / `4c32fd31`). Do not delete them —
the build breaks. The rule targets **our** Firebase project and **our** signing keys.

### Run the scanner

```bash
tools/sanitize-scan.sh            # working tree + current branch history
tools/sanitize-scan.sh --staged   # staged content only (pre-commit)
tools/sanitize-scan.sh --all      # every ref's tree + full history (before release)
```

Exit code 0 = clean. The script excludes third-party directories by pathspec
(`TMessagesProj/jni`, `TMessagesProj_Modules`, `design-skills`) and carries a
verified allowlist for upstream test credentials and webrtc fixture IPs.

**Read the two sections separately:**

- `worktree` clean → current state is safe to push.
- `history` hits → already-pushed history contains it; editing current files does not
  remove it. Record it, do not rewrite public history.

### If a secret is already in history

- **Not yet pushed** → rebuild clean history (`git bundle` backup first, then
  `git checkout --orphan`).
- **Already pushed** → do not rewrite. Remove from current files, rotate the credential,
  and record the exposure. Rewriting a public repo breaks every existing clone.

### Known false positives (do not chase these)

| Pattern | Where | Why it is safe |
|---|---|---|
| `192.168.x.x` | `TMessagesProj/jni/voip/webrtc/**` | upstream unit-test fixture IPs |
| `BEGIN ... PRIVATE KEY` (literal) | `fake_rtc_certificate_generator.h` | upstream fake certificate |
| `AIzaSy...` | `google-services.json`, `AndroidManifest.xml` | Telegram's public test key |
| `Cheng` | `jni/voip/webrtc/modules/third_party/g722/**` | copyright line `Chengxiang Lu`, CMU 1993 |
| `sk-` inside words | `design-skills/**/*.csv` | `task-based`, `risk-...` — not a key |
| `/home/gradle`, `/home/source` | `Dockerfile` | container build users |

## 2. Documentation gates

- `README.md` and `README.en.md` exist and describe the same product.
- Both READMEs link to `AGENTS.md` and `llms.txt` near the top.
- Screenshots exist and are compressed (max width 1600, quality 85, each ≤400 KB).
- `NOTICE` keeps upstream attribution and names the AI agent.
- No test counts or machine-specific paths in README.
- Every public text starts with the AI disclosure: agent, harness, model, contact,
  disclaimer.

## 3. Build and smoke test

```bash
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk
./gradlew :TMessagesProj_AppStandalone:assembleAfatStandalone
adb install -r TMessagesProj_AppStandalone/build/outputs/apk/afat/standalone/app.apk
```

- Launch, open 聊天/联系人/设置/我的/终端.
- Add and remove a local group member.
- Confirm no FATAL/ANR in `adb logcat`.
- Record versionName/versionCode and sha256 in the build report.

## 4. Publish

- Only after explicit owner authorization: create the remote repository and push the
  sanitized mirror.
- Use the owner account only as a transport channel; the AI identity stays
  `blancatora` and the model is reported as `Roost` (Perch's model layer). Never name
  the underlying model, never write a routing alias such as `team-auto`.
- Release notes and repository descriptions carry the same AI disclosure.
- Do not push an unsigned or differently-signed APK as an update to an existing install.

## 5. Commit trailers

```text
Generated-by: <model-id> (Agent: blancatora / Harness: <real harness>)
AI-Disclosure: 本提交由 AI 编码代理生成；可能含未经人工复核的改动。
```

`<model-id>` is the real model identifier in lowercase, or `Roost` when the underlying
model is not disclosed. Never a Chinese nickname, never a routing alias.
