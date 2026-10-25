# Licensing

OrionekoX is a fork of the official Telegram Android client. Two licenses apply,
because the upstream project and our original work are not under the same terms.

## Upstream: Telegram Android — GPL-2.0

`TMessagesProj/**` and the other unmodified directories come from
[DrKLO/Telegram](https://github.com/DrKLO/Telegram), licensed under **GNU GPL v2.0**
(see the top-level `LICENSE`). GPL-2.0 is a *copyleft* license: any distributed
binary that contains this code must be made available under GPL-2.0 together with
its complete corresponding source.

## OrionekoX original code — AGPL-3.0

The following files/directories are original OrionekoX work:

- `TMessagesProj/src/main/java/org/telegram/ox/**`
- `TMessagesProj/src/main/java/org/telegram/ui/Ox*.java`
- `TMessagesProj/src/main/res/xml/ox_*.xml`, `res/drawable/ox_*`,
  `res/values*/strings.xml` entries prefixed with `Ox`
- `docs/**`, `patches/**`, build/report scripts and the app icon artwork

They are offered under the **GNU Affero General Public License v3.0**
(see `LICENSE-AGPL-3.0`). You may also use them under GPL-2.0, because they are
combined with GPL-2.0 upstream code and distributed as one APK.

### Why the combined APK is still GPL-2.0

GPL-2.0-only and AGPL-3.0 are not compatible for a single combined work. The
upstream Telegram code is GPL-2.0-only, so the shipped APK as a whole is
distributed under GPL-2.0. The AGPL-3.0 grant above applies to the original
OrionekoX code on its own; it does not remove any GPL-2.0 rights in the combined
work. If you need a pure AGPL-3.0 distribution, use only the original files above
and do not link them with the upstream client.

## Third-party assets and data

- Chinese UI translations: community Telegram language packs
  ([telegram-zhCN/telegram-language-resources](https://github.com/telegram-zhCN/telegram-language-resources),
  [flyun/chatAir](https://github.com/flyun/chatAir)) plus machine translation with
  the OPUS-MT en→zh model (Helsinki-NLP, CC-BY-4.0).
- Provider brand icons: [simple-icons](https://github.com/simple-icons/simple-icons)
  (CC0-1.0) and [lobe-icons](https://github.com/lobehub/lobe-icons) (MIT).
- Telegram, Telegram Premium and all provider names are trademarks of their
  respective owners. The icons are used only to identify the configured service.
