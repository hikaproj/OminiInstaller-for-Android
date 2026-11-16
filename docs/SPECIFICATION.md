# OmniInstaller 技術仕様書 (Technical Specification)

**文書バージョン:** 2.1  
**開発者 (Author):** **Hikaproj**

---

## 1. アーキテクチャ概要

1. **Installer Engine (`com.example.installer`)**
   - `PackageParser`: ZIP/TAR/GZIP ストリーム検査、Binary XML (`AndroidManifest.xml`) 解析、端末 `Build.SUPPORTED_ABIS` との ABI 互換性判定、複数 Split APK 自動バンドル。
   - `PackageInstallerManager`: Android `PackageInstaller.Session` によるマルチ APK ストリーミング書き込み、インストール時のカスタムメタデータ・バンドル名パッチ適用、OBB 自動配置、Root (`su -c pm install-create`) サイレント導入。
2. **Store & Repository Sync Engine (`com.example.store`)**
   - `StoreRepository`: デフォルト収録アプリ（`LocalSend`, `Termux`）、GitHub Releases API 同期、独自ホスト JSON / F-Droid `index-v1.json` パーサー。
3. **Backup & Restore Engine (`com.example.backup`)**
   - `BackupManager`: インストール済みアプリの Base APK + 全 Split APK を抽出し、`manifest_omni.json` とともに標準アーカイブ形式で書き出し・復元。
4. **App Manager, Metadata Patcher & Permission Editor (`com.example.optimizer`)**
   - `AppsManagerRepository`: アプリアイコン取得、既存アプリのバンドル名・バージョン・名前・ジャンル変更の永続化および APK Binary XML パッチ生成、権限の付与 (`pm grant`) / 剥奪 (`pm revoke`)。
5. **Remote Control & Cloudflare Tunnel Server (`com.example.remote`)**
   - `RemoteInstallerServer`: `0.0.0.0` 全 NIC バインド、ポート変更、遠隔権限に応じた REST API (`/api/apps`, `/api/apps/metadata`, `/api/apps/permission` 等)、および `SharedPreferences` による設定の永続化。

---

## 2. 対応パッケージ＆バックアップアーカイブ形式

架空の拡張子を排除し、Android および Linux/アーカイブエコシステムで実在する標準形式のみをサポートしています。

### 2.1 インストール対応形式 (`ArchiveFormat`)

| # | 識別子 | 主拡張子 | 構造・仕様 |
|---|---|---|---|
| 1 | `APK` | `.apk` | 単体 Android Application Package |
| 2 | `APKS` | `.apks` | SAI / Bundletool 標準 Split APK アーカイブ (ZIP) |
| 3 | `XAPK` | `.xapk` | APKPure 互換 Split APK + OBB + `manifest.json` |
| 4 | `APKM` | `.apkm` | APKMirror 互換 Split APK バンドル |
| 5 | `AAB` | `.aab` | Android App Bundle (`base/manifest/AndroidManifest.xml` 解析) |
| 6 | `MULTI_APK_ZIP` | `.zip` | 複数 APK 同梱標準 ZIP バンドル |
| 7 | `APEX` | `.apex` | Android Pony EXpress モジュールコンテナ |
| 8 | `APKS_TAR` | `.tar` | POSIX TAR 非圧縮 Split APK アーカイブ |
| 9 | `APKS_TAR_GZ` | `.tar.gz` | GZIP 圧縮 TAR Split APK バンドル |
| 10 | `APKS_TGZ` | `.tgz` | TGZ 圧縮 Split APK パッケージ |
| 11 | `GZIP_APK` | `.apk.gz` | GZIP 圧縮単体 APK ストリーム |
| 12 | `JAR_APK` | `.jar` | Java Archive 互換 APK コンテナ |
| 13 | `OBB_ZIP` | `.obb.zip` | APK + `Android/obb/<pkg>/*.obb` 統合アーカイブ |

### 2.2 バックアップ書き出し対応形式 (`BackupExportFormat`)

| # | 拡張子 | コンテナ形式 | 用途 |
|---|---|---|---|
| 1 | `.apk` | 単体APK / ZIP | 単体アプリ高速抽出または Split バンドル |
| 2 | `.apks` | ZIP (DEFLATED) | SAI / Bundletool 完全互換 Split アーカイブ |
| 3 | `.xapk` | ZIP (DEFLATED) | OBB 対応 XAPK 互換アーカイブ |
| 4 | `.apkm` | ZIP (DEFLATED) | APKMirror 互換 Split コンテナ |
| 5 | `.zip` | ZIP (DEFLATED) | 汎用 ZIP アーカイブ |
| 6 | `.tar` | POSIX USTAR | 無圧縮高速 TAR アーカイブ |
| 7 | `.tar.gz` | USTAR + GZIP | 高圧縮 TAR.GZ アーカイブ |
| 8 | `.tgz` | USTAR + GZIP | Linux 親和性の高い TGZ アーカイブ |
| 9 | `.apk.gz` | GZIP Stream | GZIP 圧縮パッケージ |
| 10 | `.aab` | ZIP (Bundle) | App Bundle 互換アーカイブ |
| 11 | `.apex` | ZIP (APEX) | システムモジュール互換アーカイブ |
| 12 | `.jar` | ZIP (JAR) | Java Archive 互換コンテナ |
| 13 | `.ab` | Tar/Gzip Stream | Android Backup 互換ストリーム |

---

## 3. PC 遠隔操作 API 仕様 (`RemoteInstallerServer`)

設定されたポートおよび権限に応じて以下のエンドポイントを提供します。設定は `SharedPreferences` (`omni_remote_server_prefs`) に保存され、アプリ再起動後も維持されます。

- `GET /` : PC 向け Web コンソール UI（パッケージ導入 / 端末アプリ管理＆メタ情報変更 / 遠隔権限＆キャッシュ管理）
- `POST /upload` : パッケージバイナリ受信＆自動解析
- `POST /api/install-url` : 直リンク URL からの遠隔ダウンロード＆解析（`allowRemoteUrlInstall` 有効時）
- `GET /api/apps` : インストール済みアプリ・権限一覧の取得（`allowRemoteAppManagement` 有効時）
- `POST /api/apps/launch` : 指定パッケージの端末上での起動
- `POST /api/apps/uninstall` : 指定パッケージの削除要求
- `GET /api/apps/export?pkg=<packageName>` : 端末内アプリの APK 直接ダウンロード抽出
- `POST /api/apps/metadata` : アプリの表示名・バンドル名・バージョン・ジャンルの遠隔変更
- `POST /api/apps/permission` : アプリ権限の許可 (Grant) / 拒否 (Revoke) の遠隔変更（`allowRemotePermissionEdit` 有効時）
- `POST /api/system/clean-cache` : 一時ファイル＆キャッシュの遠隔クリーンアップ
