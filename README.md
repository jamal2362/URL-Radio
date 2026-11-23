<div align="center">
<img src="/fastlane/metadata/android/en-US/images/featureGraphic.png" alt="URL Radio Banner">

### ℹ️ About URL Radio
**URL Radio is a fork from Transistor.** <br>
**URL Radio is an application with a minimalist approach to listening to radio over the Internet.** <br>
**Find stations in the [radio-browser.info](https://www.radio-browser.info/) database, add them from M3U / PLS playlists, or simply tap a stream link in your web browser.** <br>
**Pull requests are welcome at any time.**<br>

**URL Radio is free software. It is released under the [MIT open source license](https://opensource.org/licenses/MIT).**
</div>

----------------------------------------

<details>
<summary>⚙️ Install URL Radio</summary>
<br>
<a href="https://github.com/jamal2362/URL-Radio/releases/latest"><img src="https://user-images.githubusercontent.com/15986930/229208526-e5a63be5-0d0b-48ab-a222-9f2f2faf0ee4.png" height="80px"></a>
<a href="https://apt.izzysoft.de/fdroid/index/apk/com.jamal2367.urlradio"><img src="https://user-images.githubusercontent.com/15986930/229206676-6dcb5180-68d6-4339-aa0c-b9a0a4eed71c.png" height="80px"></a>
</details>

----------------------------------------

<details>
<summary>✨ Features</summary>
<br>

**Listening**
- Plays MP3, AAC, Ogg (Vorbis / Opus), FLAC and HLS streams
- Shows the current song – for Icecast / Shoutcast, HLS and Ogg streams
- Sleep timer
- Home screen widget with station, current song and play / stop
- Quick settings tile to switch the radio on and off
- Android Auto and voice commands ("Play … on URL Radio")

**Recording**
- Record what is playing with one tap, or long-press for a recording with a timer
- AAC (.m4a), Opus (.ogg, Android 10+) or lossless WAV, bit rate selectable in the settings
- Recordings are saved to `Music/URL Radio` (Android 10+) or the app's own music folder

**Finding and organising stations**
- Search radio-browser.info: the most listened-to stations first, with logo and listener count
- Add stations from M3U / M3U8 / PLS playlists – station logos (`tvg-logo`) included
- Add a raw stream address – name and bit rate are taken from the stream
- Set a station logo from the gallery or from a web address
- Favourites, tags (`#jazz` in the station name) and an optional search in your station list
- Export your stations as M3U / PLS, back them up and restore them
</details>

----------------------------------------

<details>
<summary>📸 Screenshots</summary>
<br>

**Phone**
<p align="center">
<img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" width="19%" alt="Screenshot 1">
<img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" width="19%" alt="Screenshot 2">
<img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/3.png" width="19%" alt="Screenshot 3">
<img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/4.png" width="19%" alt="Screenshot 4">
<img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/5.png" width="19%" alt="Screenshot 5">
</p>

**Tablet**
<p align="center">
<img src="/fastlane/metadata/android/en-US/images/tenInchScreenshots/1.png" width="49%" alt="Tablet screenshot 1">
<img src="/fastlane/metadata/android/en-US/images/tenInchScreenshots/2.png" width="49%" alt="Tablet screenshot 2">
</p>
</details>

----------------------------------------

<details>
<summary>🚗 Enable URL Radio in Android Auto</summary>
<br>
It is possible to use URL Radio on Android Auto if you have enabled unknown sources in Android Auto developer settings.

To enable:
- Go to Android Auto settings and tap Version about 10 times to enable developer settings.
- Tap on the hamburger menu on the top right and tap on Developer Settings.
- Enable unknown sources.

URL Radio should be available in Android Auto the next time you launch it.
</details>

----------------------------------------

<details>
<summary>💡 Frequent Questions</summary>

Q: How can I add a radio station?  
A: Tap the + button. There you can search radio-browser.info, enter the address of a playlist (M3U, M3U8, PLS) or enter a raw stream address. You can also import a playlist file under Settings, or tap a stream link in your web browser. Stations added via a raw stream address do not support the update feature.

-------------------------------------------------------------------------

Q: How does the update feature work?  
A: The update feature will try to fetch the current stream address of a station as well as the updated name and station image. The feature will not work for stations added via a raw stream address.

-------------------------------------------------------------------------

Q: How does a station get a logo?  
A: Stations from radio-browser.info bring their logo along, and so do playlists that name one (`tvg-logo="…"` or an `#EXTIMG:` line). To set one yourself, turn on Settings > Advanced > Edit Stations, long-press a station, tap its cover and choose a picture from the gallery or a logo from a web address.

-------------------------------------------------------------------------

Q: Where are my recordings?  
A: On Android 10 and newer in `Music/URL Radio`, where every music player and file manager finds them. On older versions in the app's own music folder (`Android/data/com.jamal2367.urlradio/files/Music`). Format and bit rate can be chosen under Settings > Recording.

-------------------------------------------------------------------------

Q: Where do the radio station search results come from?  
A: URL Radio searches the [radio-browser.info](https://www.radio-browser.info/) online database. The most listened-to stations come first, stations radio-browser.info found unreachable are left out.  
You can help out the radio-browser.info community by [adding the missing station](https://www.radio-browser.info/add) to their database.
</details>

----------------------------------------

<details>
<summary>🔊 Supported formats</summary>
<br>

| Supported formats  | 🔊 |
| ------------------ | -- |
| AAC                | ✅ |
| AAC+               | ✅ |
| FLAC               | ✅ |
| HLS (M3U8)         | ✅ |
| M3U                | ✅ |
| MP3                | ✅ |
| OGG (Vorbis)       | ✅ |
| OPUS               | ✅ |
| PLS                | ✅ |
</details>

----------------------------------------

<details>
<summary>📜️ Credit</summary>

Base app [Transistor ❤️](https://codeberg.org/y20k/transistor)
</details>
