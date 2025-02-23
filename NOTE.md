# Novelo Dev Notes

## Android

- シークレットトークン、普通に apk 解析したりトラフィック見たりしたら取り出せる気がするけどそれでいいの？
  - とりあえず彼らが示すベストプラクティスで管理しようかな
    - https://docs.mapbox.com/help/troubleshooting/private-access-token-android-and-ios/#git-based-option
  - そう思ったけど、プロジェクト固有のシークレットをグローバル設定に書き込むのは納得いかないので、いい方法を実装したい
  - でも gradle の使い方全くわからない... 謎言語だし黒魔術感ある。
  - 解決策: `secrets.properties` にシークレットを書いて、それを gradle で読み込む
- `libs.versions.toml` は `<script type="importmap">` に近いなにからしい？


## To be considered

- 依存関係を減らす
  - security risk