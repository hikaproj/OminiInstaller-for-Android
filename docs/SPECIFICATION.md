# OmniInstaller 技術仕様書・プロトコルリファレンス (`docs/SPECIFICATION.md`)

**開発者:** Hikaproj  
**対象アプリケーション:** OmniInstaller (`hikaproj-ominiinstaller-v[バージョン].apk`)  
**カスタムURIスキーム:** `hpjominii://`

本ドキュメントでは、**OmniInstaller** がサポートするカスタムURIスキーム (`hpjominii://`)、独自ストアリポジトリJSONフォーマット、一括インポート仕様、対応する25種類以上のインストールパッケージ形式、24種類のバックアップ書き出し形式、およびPC遠隔インストーラー（Cloudflare Tunnel連携含む）のAPI仕様を網羅的に解説します。

---

## 目次

1. [カスタムURIスキーム (`hpjominii://`) 仕様](#1-カスタムuriスキーム-hpjominii-仕様)
   - [1.1 リポジトリ・アプリリスト追加ページ (`hpjominii://store/repo`)](#11-リポジトリアプリリスト追加ページ-hpjominiistorerepo)
   - [1.2 アプリ専用詳細ページ直接表示 (`hpjominii://store/app`) — リポジトリ未登録対応](#12-アプリ専用詳細ページ直接表示-hpjominiistoreapp--リポジトリ未登録対応)
   - [1.3 直リンクからの直接インストール (`hpjominii://install`)](#13-直リンクからの直接インストール-hpjominiiinstall)
   - [1.4 Webサイト・Markdownへの埋め込み例](#14-webサイトmarkdownへの埋め込み例)
2. [アプリストア リポジトリJSON仕様](#2-アプリストア-リポジトリjson仕様)
   - [2.1 OmniInstaller 標準リポジトリ形式 (推奨・大規模カタログ対応)](#21-omniinstaller-標準リポジトリ形式-推奨大規模カタログ対応)
   - [2.2 シンプルJSON配列形式](#22-シンプルjson配列形式)
   - [2.3 F-Droid `index-v1.json` 互換形式](#23-f-droid-index-v1json-互換形式)
   - [2.4 Obtainium / GitHub リポジトリリスト形式](#24-obtainium--github-リポジトリリスト形式)
   - [2.5 一括テキストインポート形式 (Bulk Import)](#25-一括テキストインポート形式-bulk-import)
3. [対応インストール形式一覧 (全26種)](#3-対応インストール形式一覧-全26種)
4. [対応バックアップ書き出し形式一覧 (全24種)](#4-対応バックアップ書き出し形式一覧-全24種)
5. [PC遠隔インストール & Cloudflare Tunnel 仕様](#5-pc遠隔インストール--cloudflare-tunnel-仕様)

---

## 1. カスタムURIスキーム (`hpjominii://`) 仕様

OmniInstaller は Android OS の `<intent-filter>` に `hpjominii://` スキームを登録しており、ブラウザ・SNS・QRコード・外部アプリ・Markdownリンクからワンタップで各機能を呼び出すことができます。

### 1.1 リポジトリ・アプリリスト追加ページ (`hpjominii://store/repo`)

1つまたは複数の独自リポジトリJSON URL、およびGitHubリポジトリ (`owner/repo`) を一括でストアへ登録します。起動すると確認ダイアログが表示され、ユーザーの承認後に一括同期が開始されます。

#### エンドポイント形式
```text
hpjominii://store/repo?[クエリパラメータ]
hpjominii://repo/add?[クエリパラメータ]
hpjominii://store?[クエリパラメータ]
```

#### クエリパラメータ

| パラメータ名 | 必須 | 複数指定 | 説明 |
| :--- | :---: | :---: | :--- |
| `url` | 任意 | カンマ区切りまたは複数パラメータ可 | 独自ホストのJSONリポジトリURL (`https://.../repo.json`) |
| `urls` | 任意 | カンマ区切り可 | 複数のJSONリポジトリURLを一括指定 (`url1,url2,url3`) |
| `github` | 任意 | カンマ区切り可 | GitHubリポジトリ (`owner/repo1,owner/repo2`) を一括指定 |
| `repo` / `repos` | 任意 | カンマ区切り可 | HTTP URL または `owner/repo` を自動判別して一括追加 |

#### 使用例
- **単一リポジトリの追加:**
  ```text
  hpjominii://store/repo?url=https://example.com/hikaproj-repo.json
  ```
- **複数JSONリポジトリ＋複数GitHubリポジトリの同時一括追加:**
  ```text
  hpjominii://store/repo?urls=https://repo1.example.com/index.json,https://repo2.example.com/apps.json&github=TeamNewPipe/NewPipe,JunkFood02/Seal,ImranR98/Obtainium
  ```

---

### 1.2 アプリ専用詳細ページ直接表示 (`hpjominii://store/app`) — リポジトリ未登録対応

ストア内の特定アプリの**専用詳細ページ**を直接開きます。
**重要:** ユーザーの端末に該当リポジトリがまだ追加されていない状態でも、クエリパラメータまたは `github` 指定から動的にアプリ詳細ページを生成して表示・インストールできます。

#### エンドポイント形式
```text
hpjominii://store/app?[クエリパラメータ]
hpjominii://app?[クエリパラメータ]
```

#### クエリパラメータ

| パラメータ名 | 必須 | 説明 |
| :--- | :---: | :--- |
| `id` | 任意 | ストア内のアプリID (例: `newpipe`, `termux`)。登録済みの場合は即座に開きます |
| `github` | 任意 | GitHubの `owner/repo` (例: `TeamNewPipe/NewPipe`)。リポジトリ未登録でもGitHub APIから最新リリース・アセット一覧をリアルタイム取得して専用ページを構成します |
| `name` | 任意 | アプリの表示名 (未登録アプリの動的ページ生成用) |
| `pkg` / `package` | 任意 | Android パッケージ名 (例: `com.hikaproj.sample`)。端末内の導入済みバージョンとの比較にも使用されます |
| `version` | 任意 | 最新バージョン表記 (例: `v1.2.0`) |
| `apk` / `url` | 任意 | パッケージファイル (`.apk`, `.xapk`, `.apks`, `.omni` 等) の直リンクURL |
| `author` | 任意 | 開発者名 (例: `Hikaproj`) |
| `summary` | 任意 | 短い概要テキスト |
| `desc` / `description` | 任意 | 専用ページに表示する詳細説明テキスト |
| `icon` | 任意 | アプリアイコン画像のURL (`https://...`) |
| `license` | 任意 | ライセンス表記 (例: `MIT`, `GPL-3.0`, `Apache-2.0`) |
| `category` | 任意 | カテゴリ名 (例: `ツール`, `メディア`) |
| `size` | 任意 | ファイルサイズ (バイト単位の数値) |
| `changelog` | 任意 | リリースノート / 更新履歴 |

#### 使用例
1. **リポジトリ未登録の端末で、GitHubリポジトリから直接アプリ詳細ページを開く:**
   ```text
   hpjominii://store/app?github=ImranR98/Obtainium
   ```
2. **リポジトリ未登録の端末で、パラメータのみで完全な専用ページを動的生成して開く:**
   ```text
   hpjominii://store/app?name=HikaprojTool&pkg=com.hikaproj.tool&version=v2.1.0&author=Hikaproj&license=MIT&apk=https://example.com/hikaproj-tool-v2.1.0.apk&desc=Hikaproj公式ユーティリティアプリの専用配布ページです。
   ```

---

### 1.3 直リンクからの直接インストール (`hpjominii://install`)

任意のHTTP/HTTPS直リンクにあるパッケージファイル（`.apk`, `.xapk`, `.apks`, `.apkm`, `.omni`, `.hpjpkg`, `.tar.gz` 等）を直接ダウンロードし、インストーラーの解析画面へ渡します。

#### エンドポイント形式
```text
hpjominii://install?url=[パッケージ直リンクURL]
```

#### 使用例
```text
hpjominii://install?url=https://github.com/TeamNewPipe/NewPipe/releases/download/v0.27.2/NewPipe_v0.27.2.apk
```

---

### 1.4 Webサイト・Markdownへの埋め込み例

WebページやGitHub READMEに配布ボタンを設置する場合、以下のようなHTMLまたはMarkdownを記述できます。

```html
<!-- リポジトリ一括追加ボタン -->
<a href="hpjominii://store/repo?url=https://example.com/repo.json&github=TeamNewPipe/NewPipe">
  OmniInstaller でリポジトリを追加
</a>

<!-- リポジトリ未登録でも開けるアプリ専用ページボタン -->
<a href="hpjominii://store/app?name=MyApp&pkg=com.example.myapp&version=v1.0.0&author=Hikaproj&apk=https://example.com/myapp.apk">
  OmniInstaller でアプリ詳細を開く
</a>
```

---

## 2. アプリストア リポジトリJSON仕様

OmniInstaller のアプリストアは、個人規模のリポジトリから数千件のアプリを収録する大規模リポジトリまで高速に処理できるよう設計されています。以下の4種類のJSONスキーマを自動判別して同期します。

### 2.1 OmniInstaller 標準リポジトリ形式 (推奨・大規模カタログ対応)

```json
{
  "repo_name": "Hikaproj Custom App Repository",
  "description": "Hikaproj 公式および厳選アプリの配布インデックス",
  "apps": [
    {
      "id": "hikaproj_sample",
      "name": "Hikaproj Sample Utility",
      "package_name": "com.hikaproj.sample",
      "summary": "高速システムユーティリティ",
      "description": "専用詳細ページに表示される複数行の詳しい解説テキストです。\n機能一覧や使い方を記載できます。",
      "category": "ユーティリティ",
      "author": "Hikaproj",
      "icon_url": "https://example.com/icons/sample.png",
      "github_repo": "hikaproj/sample-utility",
      "website_url": "https://example.com",
      "license": "Apache-2.0",
      "min_sdk": 24,
      "architecture": "arm64-v8a / Universal",
      "version": "v1.4.0",
      "download_url": "https://example.com/releases/sample-v1.4.0.apk",
      "size": 15728640,
      "changelog": "・新UIの採用\n・Split APKインストーラーの高速化",
      "date": "2025-02-01",
      "versions": [
        {
          "version": "v1.4.0",
          "download_url": "https://example.com/releases/sample-v1.4.0-arm64.apk",
          "size": 15728640,
          "date": "2025-02-01",
          "changelog": "最新安定版",
          "abi": "arm64-v8a"
        },
        {
          "version": "v1.3.0",
          "download_url": "https://example.com/releases/sample-v1.3.0-universal.apk",
          "size": 18200000,
          "date": "2025-01-10",
          "changelog": "旧バージョン",
          "abi": "Universal"
        }
      ]
    }
  ]
}
```

### 2.2 シンプルJSON配列形式

ルート要素が `[...]` の配列である場合もそのままアプリリストとして同期されます。また、文字列配列 `["TeamNewPipe/NewPipe", "JunkFood02/Seal"]` を渡した場合は各GitHubリポジトリの最新リリースを自動解決します。

### 2.3 F-Droid `index-v1.json` 互換形式

F-Droid 標準の `{"repo": {"name": "...", "address": "..."}, "apps": [...], "packages": {...}}` 構造を自動認識し、`address` と `apkName` を結合してダウンロードリンクを生成します。

### 2.4 Obtainium / GitHub リポジトリリスト形式

Obtainium のエクスポート形式 `{"configs": [{"url": "https://github.com/owner/repo"}, ...]}` をそのまま貼り付けて一括インポートできます。

### 2.5 一括テキストインポート形式 (Bulk Import)

ストア画面の「**リポジトリ・アプリ一括追加**」ダイアログでは、改行・カンマ・セミコロン区切りで以下を混在して一度に登録できます。

```text
# シャープで始まる行はコメントとして無視されます
TeamNewPipe/NewPipe
https://github.com/JunkFood02/Seal
https://my-custom-host.example.com/omni-repo.json
ImranR98/Obtainium
```

---

## 3. 対応インストール形式一覧 (全26種)

OmniInstaller の `PackageParser` および `PackageInstallerManager` は、単体APKから複数Split APK、OBB同梱アーカイブ、POSIX TAR / GZIP圧縮アーカイブまで以下の **26種類** のフォーマットをネイティブ解析・インストールできます。

| # | 識別子 | 主な拡張子 | 形式名称・構造 |
| :-: | :--- | :--- | :--- |
| 1 | `APK` | `.apk` | 標準 Android 単体パッケージ |
| 2 | `SPLIT_APKS` | `.apks` | Split APKs バンドルアーカイブ (SAI / Bundletool 互換) |
| 3 | `XAPK` | `.xapk` | XAPK パッケージ (`manifest.json` + Split APK + OBB データ同梱) |
| 4 | `APKM` | `.apkm` | APKMirror バンドルパッケージ (`info.json` + Split APK) |
| 5 | `AAB` | `.aab` | Android App Bundle アーカイブ (base/split 自動抽出対応) |
| 6 | `BUNDLE_ZIP` | `.zip` | ZIP圧縮バンドル (内部の `.apk` やネストされた `.apks`/`.xapk` も再帰展開) |
| 7 | `OMNI` | `.omni` | OmniInstaller 独自拡張パッケージ (`omni_meta.json` 同梱) |
| 8 | `HPJPKG` | `.hpjpkg` | Hikaproj 公式署名付き配布コンテナ (`hikaproj_manifest.json` 同梱) |
| 9 | `APEX` | `.apex` | Android Pony EXpress モジュールコンテナ |
| 10 | `APKS_TAR` | `.tar` | POSIX TAR 非圧縮マルチAPKアーカイブ |
| 11 | `APKS_TAR_GZ` | `.tar.gz` | TAR + GZIP 圧縮マルチAPKバンドル |
| 12 | `APKS_TGZ` | `.tgz` | TGZ 圧縮パッケージバンドル |
| 13 | `APKS_GZIP` | `.apk.gz` | GZIP 単一圧縮 APK ストリーム |
| 14 | `APK_PLUS` | `.apk+` | APK+ 拡張メタデータ同梱コンテナ |
| 15 | `ZAPK` | `.zapk` | ZAPK 高圧縮マルチAPKアーカイブ |
| 16 | `SAPK` | `.sapk` | SAPK Split APK パッケージ |
| 17 | `MAPK` | `.mapk` | MAPK Multi-APK コンテナ |
| 18 | `BAPK` | `.bapk` | BAPK バンドルアーカイブ |
| 19 | `PACK` | `.pack` | 汎用 `.pack` アプリコンテナ |
| 20 | `JAR_APK` | `.jar` | JAR 署名済み APK コンテナ |
| 21 | `OBB_ZIP` | `.obb.zip` | APK + `Android/obb/<package>/main.*.obb` 自動配置アーカイブ |
| 22 | `MULTI_APK_DIR` | `(複数選択)` | 端末内の複数 `.apk` ファイル (`base.apk` + `config.*.apk`) 同時選択バンドル |
| 23 | `BACKUP_AB` | `.ab` | Android Backup コンテナ互換アーカイブ |
| 24 | `OMNIBAK` | `.omnibak` | OmniInstaller 専用フルバックアップアーカイブ |
| 25 | `HPJBAK` | `.hpjbak` | Hikaproj 専用世代管理バックアップコンテナ |
| 26 | `BIN_APK` | `.bin` | バイナリストリームパッケージ (ZIP/GZIP/TAR マジックバイト自動判定) |

### 複数APK (Split APKs) インストールバグ対策仕様
1. **正確なセッションサイズ計算:** 圧縮アーカイブのサイズではなく、展開後の全対象 APK ファイルの厳密な合計バイト数を `SessionParams.setSize()` に設定し、`INSTALL_FAILED_INVALID_APK` やストリーム切断を防止します。
2. **非互換 ABI Split の自動フィルタリング:** `arm64-v8a` 端末に対して `x86` や `x86_64` のネイティブライブラリ Split が同じアーカイブに含まれている場合、`Build.SUPPORTED_ABIS` と照合して非互換 ABI Split を自動除外し、`INSTALL_FAILED_NO_MATCHING_ABIS` や競合エラーを防ぎます。
3. **ベースAPK優先順序付け:** `base.apk`（または `split` 属性を持たないメインAPK）を確実に先頭 (`base.apk`) としてセッションに書き込みます。

---

## 4. 対応バックアップ書き出し形式一覧 (全24種)

「アプリ管理」および「最適化・復元」タブでは、インストール済みアプリ（単一APKアプリおよび複雑なSplit APKアプリの両方）を以下の **24種類** のフォーマットから自由に選択して書き出すことができます。

| # | 形式名 | 拡張子 | 構造・特徴 |
| :-: | :--- | :--- | :--- |
| 1 | 単体 APK | `.apk` | ベースAPKを単一ファイルとして直接抽出 |
| 2 | Split APKs | `.apks` | SAI / Bundletool 互換の Split APK 一式 ZIP |
| 3 | XAPK | `.xapk` | `manifest.json` メタデータ付きの XAPK コンテナ |
| 4 | APKM | `.apkm` | `info.json` 同梱の APKMirror 互換コンテナ |
| 5 | 標準 ZIP | `.zip` | 汎用 ZIP アーカイブ (`base.apk` + `split_*.apk`) |
| 6 | Omni Package | `.omni` | `omni_meta.json` 同梱の OmniInstaller 標準パッケージ |
| 7 | Hikaproj Package | `.hpjpkg` | `hikaproj_manifest.json` 同梱の Hikaproj 独自パッケージ |
| 8 | Omni Backup | `.omnibak` | 復元メタデータ同梱バックアップアーカイブ |
| 9 | Hikaproj Backup | `.hpjbak` | Hikaproj 世代管理用バックアップアーカイブ |
| 10 | POSIX TAR | `.tar` | 無圧縮 512バイトブロック POSIX TAR アーカイブ |
| 11 | TAR.GZ | `.tar.gz` | POSIX TAR + GZIP 圧縮アーカイブ |
| 12 | TGZ | `.tgz` | TGZ 圧縮バックアップバンドル |
| 13 | APK.GZ | `.apk.gz` | ベースAPKの単一 GZIP 高速圧縮ファイル |
| 14 | AAB 互換コンテナ | `.aab` | バンドルメタデータ付き AAB 互換コンテナ |
| 15 | ZAPK | `.zapk` | ZAPK 形式マルチAPKアーカイブ |
| 16 | SAPK | `.sapk` | SAPK 形式スプリットAPKアーカイブ |
| 17 | MAPK | `.mapk` | MAPK 形式マルチAPKバンドル |
| 18 | BAPK | `.bapk` | BAPK 形式バンドルアーカイブ |
| 19 | APK+ | `.apk+` | APK+ 拡張パッケージアーカイブ |
| 20 | APEX 互換 | `.apex` | モジュールアーカイブ互換出力 |
| 21 | JAR コンテナ | `.jar` | Java Archive 互換パッケージコンテナ |
| 22 | PACK コンテナ | `.pack` | 汎用 `.pack` アーカイブ |
| 23 | AB バックアップ | `.ab` | Android Backup 互換コンテナ |
| 24 | BIN イメージ | `.bin` | バイナリパッケージイメージ |

---

## 5. PC遠隔インストール & Cloudflare Tunnel 仕様

### 5.1 ネットワークバインドとポート変更仕様
- **バインドアドレス:** `0.0.0.0`（全ネットワークインターフェース）に明示的にバインドし、`WifiManager` および `NetworkInterface` から `wlan0`（Wi-Fi）、`ap0`（テザリング/ホットスポット）、`eth0`（有線LAN）等の全ローカルIPv4アドレスを列挙します。
- **ポート変更:** UI上から `1024` 〜 `65535` の任意のTCPポートに動的変更・即時再起動が可能です（デフォルト: `8080`）。

### 5.2 HTTP エンドポイント一覧

| メソッド | パス | 説明 |
| :--- | :--- | :--- |
| `GET` | `/` | PCブラウザ用のドラッグ＆ドロップ Web UI を返却 |
| `GET` | `/status` | サーバー稼働状態・ポート・CF Tunnel状態をJSONで返却 |
| `POST` | `/upload?name=[filename]` | バイナリストリームまたは `multipart/form-data` でパッケージをアップロードし、端末側で即座に解析・インストールを開始 |
| `POST` | `/install-url?url=[direct_url]` | PC側から指定したAPK/パッケージ直リンクURLをAndroid端末側で直接ダウンロード＆インストール |
| `OPTIONS` | `/*` | CORS プリフライトリクエスト対応 (`Access-Control-Allow-Origin: *`) |

### 5.3 Cloudflare Tunnel (`cloudflared`) 連携と著作権・ライセンス遵守方針
- **商標・著作権表記:** Cloudflare® は Cloudflare, Inc. の登録商標です。`cloudflared` は Cloudflare, Inc. によって **Apache License 2.0** の下で公開されているオープンソースソフトウェアです。
- **コンプライアンス設計:** OmniInstaller は第三者のプロプライエタリバイナリを無断でAPK内部にバンドル・再配布しません。代わりに以下の2つのクリーンな方式で連携します。
  1. **外部リバースプロキシ / カスタムホスト連携:** PC上またはルーター/サーバー上で稼働する `cloudflared tunnel --url http://<Android_IP>:<PORT>` からの `X-Forwarded-For` / `CF-Connecting-IP` リクエストを受け付け、公開URLをUI上で共有・管理します。
  2. **ローカル `cloudflared` バイナリ連携:** ユーザーが Termux 等を通じて自身の端末内に導入したオープンソース版 `cloudflared` が存在する場合、Quick Tunnel (`trycloudflare.com`) または Zero Trust Tunnel Token (`--token`) を用いてプロセスを起動し、標準エラー出力から発行された `https://*.trycloudflare.com` URLを自動検出します。
