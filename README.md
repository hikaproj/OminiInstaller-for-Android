# OmniInstaller (`hikaproj-ominiinstaller-v[バージョン].apk`)

**開発者 (Developer):** **Hikaproj**  
**詳細技術仕様書:** [**docs/SPECIFICATION.md (hpjominii:// スキーム・リポジトリJSON・全対応形式の詳細仕様はこちら)**](./docs/SPECIFICATION.md)

---

## 概要 (Overview)

**OmniInstaller** は、**Hikaproj** によって設計・開発された、Android 向けの次世代・万能パッケージインストーラー、マルチリポジトリ対応アプリストア、24形式対応バックアップ＆リストアマネージャー、および Cloudflare Tunnel 対応の PC 遠隔導入スイートです。

単体の `.apk` はもちろん、`.xapk`（OBBデータ同梱）、`.apks`（Split APKs）、`.apkm`、`.aab`、`.omni`、`.hpjpkg`、`.tar.gz` など **26種類以上のインストール形式** と、端末内の**複数APKファイル同時選択インストール**、さらには **APK直リンクからの直接ダウンロード導入** に対応しています。また、ビルド成果物および GitHub Actions リリースでは自動的に `hikaproj-ominiinstaller-v[バージョン].apk` の形式で出力されます。

---

## 主な機能 (Key Features)

### 1. 26種類以上のパッケージ形式＆複数APK・直リンク対応インストーラー
- **26種類のパッケージ形式をネイティブ解析:**
  - `.apk`, `.apks`, `.xapk`, `.apkm`, `.aab`, `.zip`, `.omni`, `.hpjpkg`, `.apex`, `.tar`, `.tar.gz`, `.tgz`, `.apk.gz`, `.apk+`, `.zapk`, `.sapk`, `.mapk`, `.bapk`, `.pack`, `.jar`, `.obb.zip`, `.ab`, `.omnibak`, `.hpjbak`, `.bin`、および複数APKファイルの同時選択バンドルに対応。
- **［複数のAPK］(Split APKs / Multi-APK) インストールバグの完全対策:**
  - 端末内でバラバラに保存された `base.apk` と `split_config.*.apk` を複数同時選択して1つのセッションで統合インストール可能。
  - アーカイブ内に端末非対応のCPUアーキテクチャ（例: `arm64-v8a` 端末における `x86` Split）が混在している場合、`Build.SUPPORTED_ABIS` と照合して非互換 Split を自動除外し、展開後の正確な合計サイズで `PackageInstaller.Session` を構築します。
- **APK / パッケージ直リンクからの直接導入:**
  - HTTP/HTTPS の直リンクURLを入力するだけで、プログレスバー表示付きでダウンロードし、完了と同時にパッケージ解析・インストールへ移行します。
- **Root / 非Root ハイブリッド対応:**
  - 非Root環境では OS 標準の `PackageInstaller` セッション API による安全な導入を行い、Root環境（Magisk / KernelSU / APatch）では `pm install-create` を用いたサイレントインストールを選択できます。

### 2. 次世代アプリストア（専用詳細ページ・一括追加・`hpjominii://` 対応）
- **アプリごとの専用詳細ページ (`StoreAppDetailScreen`):**
  - ストア内のアプリをタップすると専用ページへ遷移し、詳細な機能解説、ライセンス、要求SDK、対応アーキテクチャ、リリースノート、過去のバージョン／ABI別ビルド一覧、GitHub/公式サイトへのリンク、および共有用の `hpjominii://` リンクを確認・導入できます。
- **リポジトリ・アプリリストの一括追加 & 大規模カタログ対応:**
  - 複数の GitHub リポジトリ (`owner/repo`)、独自ホスト JSON URL、F-Droid `index-v1.json`、Obtainium エクスポート JSON などを改行・カンマ区切りで一度に一括登録・同期できます。
- **`hpjominii://` カスタムURIディープリンク完全対応:**
  - `hpjominii://store/repo?urls=...&github=...` から複数リポジトリの一括追加画面を起動。
  - `hpjominii://store/app?...` からアプリの専用詳細ページを直接表示（**まだリポジトリを追加していない端末でも、クエリパラメータやGitHub指定から動的に専用ページを生成して開くことが可能**）。
  - `hpjominii://install?url=...` から直リンクパッケージを直接ダウンロード＆インストール。
  - 詳しいパラメータ仕様は [**docs/SPECIFICATION.md**](./docs/SPECIFICATION.md) をご参照ください。

### 3. 24種類の書き出し形式に対応するバックアップ＆リストア
- インストール済みアプリ（Split APK 構成アプリを含む）を以下の **24種類** のアーカイブ形式で書き出し・共有・復元できます：
  - `.omni`, `.hpjpkg`, `.xapk`, `.apks`, `.apkm`, `.apk`, `.zip`, `.omnibak`, `.hpjbak`, `.tar`, `.tar.gz`, `.tgz`, `.apk.gz`, `.aab`, `.zapk`, `.sapk`, `.mapk`, `.bapk`, `.apk+`, `.apex`, `.jar`, `.pack`, `.ab`, `.bin`
- Root環境では `/data/data/<package>` のアプリデータ領域を含めたフルアーカイブにも対応します。

### 4. PC遠隔インストール（ポート変更・全NIC対応・Cloudflare Tunnel 機能）
- **アクセス不能バグの解消とマルチIP表示:**
  - サーバーソケットを `0.0.0.0`（全インターフェース）にバインドし、Wi-Fi (`wlan0`) やホットスポット (`ap0`) など端末が持つすべてのローカルIPv4アドレスを自動検出して一覧表示します。
- **自由なポート番号変更:**
  - `1024` 〜 `65535` の任意のポート番号へUI上からワンタップで変更・再バインドできます。
- **PCブラウザからのドラッグ＆ドロップ導入＋直リンク遠隔送信:**
  - PCブラウザで表示されるモダンなWeb UIへパッケージをドロップするか、PC側からAPK直リンクURLを送信するだけで、Android端末側へ即座に展開されます。
- **Cloudflare Tunnel (`cloudflared`) 連携機能（著作権・ライセンス完全準拠）:**
  - 外出先や別セグメントのPCからも安全にHTTPS経由でパッケージを遠隔送信できるよう、Cloudflare Tunnel 連携機能を搭載しています。
  - Zero Trust トンネルトークン、カスタムパブリックホスト名、および Quick Tunnel (`trycloudflare.com`) コマンド生成をサポートしています。

---

## 詳細仕様書へのリンク (Specification Document)

`hpjominii://` ディープリンクの全エンドポイント、クエリパラメータ、リポジトリ未登録時のアプリ詳細ページ呼び出し方法、ストアリポジトリのJSONスキーマ、および全フォーマットの技術仕様については、以下の専用ドキュメントをご覧ください。

👉 **[OmniInstaller 技術仕様書・プロトコルリファレンス (`docs/SPECIFICATION.md`)](./docs/SPECIFICATION.md)**

---

## ビルド方法と出力APKファイル名 (Build & Release)

本プロジェクトでは、Gradle 設定 (`app/build.gradle.kts`) および GitHub Actions ワークフロー (`.github/workflows/build-release.yml`) の双方において、生成されるAPKファイル名が以下の規則に従うよう構成されています。

```text
hikaproj-ominiinstaller-v[バージョン].apk
```
（例: `hikaproj-ominiinstaller-v1.0.apk` または CI ビルド時 `hikaproj-ominiinstaller-v1.0.15.apk`）

### ローカルでのビルド手順
```bash
# デバッグビルド
gradle assembleDebug

# リリースビルド
gradle assembleRelease
```

---

## 著作権・オープンソースライセンスに関する表記 (Copyright & Licenses)

- **アプリケーション開発・著作権:**
  - Copyright © **Hikaproj**. All rights reserved.
- **Cloudflare Tunnel (`cloudflared`) に関する表記:**
  - Cloudflare® は Cloudflare, Inc. の登録商標です。
  - `cloudflared` は Cloudflare, Inc. により [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) の下で公開されているオープンソースソフトウェアです。
  - 本アプリケーションは、第三者のプロプライエタリバイナリを無断でアプリ内部に同梱・再配布することなく、標準HTTPリバースプロキシ仕様およびユーザー環境（PC または Termux 等）に導入された `cloudflared` クライアントとのプロセス連携によって著作権およびライセンス条項を完全に遵守しています。
