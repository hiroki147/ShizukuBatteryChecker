# 🔋 Shizuku Battery Checker

**Shizukuを利用してAndroid端末のバッテリー情報を取得・解析するアプリ**

Androidのバッテリー情報をShizuku経由で取得し、**設計容量・実効容量・バッテリー健康度・推定劣化率**などを表示します。

---

## 📥 APKを入手

ビルド済みのAPKを利用したい場合は、GitHubリポジトリの **Releases（リリース）** からダウンロードできます。

### 👉 [Releases / Resources]

GitHubのリリースページにある **Assets** からAPKをダウンロードしてください。

```text
Releases
 └── 最新バージョン
      └── Assets
           └── ShizukuBatteryChecker.apk
```

ダウンロードしたAPKをAndroid端末に転送してインストールできます。

> ⚠️ GitHub ReleasesからダウンロードしたAPKは、使用している端末のAndroidのセキュリティ設定によっては「提供元不明のアプリ」として扱われる場合があります。

---

## 🔨 ソースコードからビルド

Android Studioを使用してビルドできます。

### 必要なもの

* Android Studio
* Android SDK
* JDK
* Git
* Android端末
* Shizuku

### 1. リポジトリをクローン

```bash
git clone https://github.com/your-name/Shizuku-Battery-Checker.git
cd Shizuku-Battery-Checker
```

※ `your-name/Shizuku-Battery-Checker` は実際のGitHubリポジトリに置き換えてください。

---

### 2. Android Studioで開く

Android Studioを起動し、

**Open**

からクローンしたプロジェクトのフォルダを開きます。

Gradleの同期が完了するまで待ちます。

---

### 3. APKをビルド

Android Studioのメニューから、

```text
Build
 ↓
Build Bundle(s) / APK(s)
 ↓
Build APK(s)
```

を選択します。

ビルドが成功すると、APKが生成されます。

通常は以下の場所にあります。

```text
app/build/outputs/apk/debug/app-debug.apk
```

---

### 💻 コマンドラインでビルド

Android Studioを使用せず、ターミナルからビルドすることもできます。

Linux / macOS:

```bash
./gradlew assembleDebug
```

Windows:

```powershell
.\gradlew.bat assembleDebug
```

生成されたAPK：

```text
app/build/outputs/apk/debug/app-debug.apk
```

---

## 📦 Release APKを作成

配布用のAPKを作成する場合は、

```bash
./gradlew assembleRelease
```

を実行します。

生成先：

```text
app/build/outputs/apk/release/app-release.apk
```

> 🔐 Release APKを一般公開する場合は、Androidアプリとして署名する必要があります。

---

## ✨ 主な機能

* 🔋 バッテリー残量の表示
* ❤️ バッテリー健康度の計算・表示
* 📐 バッテリー設計容量の表示
* 📊 実効容量の表示
* 📉 推定劣化率の表示
* 📱 端末メーカー・モデル名の表示
* 📁 使用しているバッテリーsysfsノードの表示
* ✏️ 設計容量の手動入力
* 💾 手動入力した設計容量の保存
* 🔄 バッテリー情報の再取得

---

## 🚀 使い方

1. Shizukuをインストール・起動
2. Shizuku Battery Checkerを起動
3. Shizukuの権限を許可
4. バッテリー情報を確認

Shizukuが起動していない場合は、Shizukuを起動してから**「再確認」**を押してください。

---

## 🧮 健康度の計算

```text
健康度 = 実効容量 ÷ 設計容量 × 100
```

例えば、

```text
設計容量: 5000 mAh
実効容量: 4500 mAh
```

の場合、

```text
4500 ÷ 5000 × 100 = 90%
```

となります。

推定劣化率は、

```text
劣化率 = 100 - 健康度
```

です。

> ⚠️ 健康度・劣化率は取得した容量情報から計算した推定値です。バッテリーの物理的な状態を直接測定したものではありません。

---

## ✏️ 設計容量の手動入力

端末から設計容量を取得できない場合は、手動で入力できます。

例えば、

```text
5000
```

と入力して**「適用」**を押してください。

入力した設計容量は保存され、次回以降も使用されます。

---

## 🔧 技術構成

* Kotlin
* Jetpack Compose
* AndroidX
* ViewModel
* Kotlin Coroutines
* StateFlow
* SharedPreferences
* Shizuku API

---

## 🔐 Shizukuについて

本アプリはShizuku経由でシステム情報を取得します。

アプリ自身がroot権限を取得するわけではありません。

Shizukuが起動しており、アプリに必要な権限が許可されている必要があります。

---

## ⚠️ 注意事項

Android端末やメーカーによって、取得できるバッテリー情報は異なります。

そのため、端末によっては、

* 設計容量が取得できない
* 実効容量が取得できない
* 健康度を計算できない
* バッテリーノードが異なる

などの問題が発生する場合があります。

---

## 📜 ライセンス

このプロジェクトのライセンスについては、リポジトリ内の `LICENSE` ファイルを参照してください。

---

## 🔗 Resources

| リソース               | 内容              |
| ------------------ | --------------- |
| 📥 **リリース**    | ビルド済みAPKをダウンロード |
| 💻 **Source Code** | アプリのソースコード      |
| 🐛 **Issues**      | バグ報告・問題の報告      |

---

**🔋 Shizuku Battery Checker**

*Androidのバッテリーをもっと詳しく。*
