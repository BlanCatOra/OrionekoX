# Releasing and publishing OrionekoX

This checklist applies to public pushes, GitHub releases and any text that becomes
publicly visible. The repository is GPL-2.0; keep upstream notices intact.

## 1. Sanitize

Never publish:

- keystores (`*.keystore`, `*.jks`, `*.p12`) or signing passwords;
- `google-services.json` / Firebase API keys;
- private email addresses, phone numbers, internal IPs or home-directory paths;
- provider API keys, OAuth tokens, cookies or `~/.dsh` credentials;
- user data backups (`*.oxagent`, chat databases, agent workspaces).

Scan the working tree and history before pushing:

```bash
git grep -nE 'AIza[0-9A-Za-z_-]{20,}|sk-[A-Za-z0-9]{20,}|BEGIN (RSA |EC )?PRIVATE KEY'
git log --all -p | grep -nE 'AIza[0-9A-Za-z_-]{20,}|sk-[A-Za-z0-9]{20,}'
```

If a secret is already in history, rewrite a separate public mirror with
`git filter-repo`; do not push the original history and delete the file afterwards.
The local development repository may keep its original history.

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
  `blancatora` and the model `deepseek-v4.1-flash`.
- Release notes and repository descriptions carry the same AI disclosure.
- Do not push an unsigned or differently-signed APK as an update to an existing install.

## 5. Commit trailers

```text
Generated-by: blancatora (DeepSeek Harness / deepseek-v4.1-flash)
AI-Disclosure: 本提交由 AI 编码代理生成；可能含未经人工复核的改动。
```
