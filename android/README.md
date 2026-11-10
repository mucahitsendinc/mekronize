# Mekronize Android

Çevrimdışı öncelikli müzik/video oynatıcı. Açılışta doğrudan yerel kütüphane gelir; senkron ve ayarlar ana ekrandaki kutulardan açılır.

- **Dil/araçlar:** Kotlin, XML View (Compose yok), Media3 ExoPlayer, SQLite, OkHttp. minSdk 21 (Android 5), targetSdk 34.
- **Ana ekran:** CarPlay tarzı ikonlu kutular: Playlistler · Tümü · Videolar · Müzikler · Favoriler (en çok çalınan üstte; ❤ ile işaretlenenler önce) · Karışık çal · Senkron · Ayarlar.
- **Oynatıcı:** Her ekranın altında kendi kontrolleri ve ilerleme çizgisi olan küçük oynatıcı; dokununca ya da yukarı çekince tam ekran oynatıcıya (kapak/video, ilerleme çubuğu, karışık, tekrar, favori) büyür.
- **Karışık çalma:** sıra oturum boyunca sabit; ⏮ önceki çalınana döner. Tekrar: kapalı / hepsi / tek.
- **Depolama:** `Android/data/com.mekronize.app/files/Mekronize` (izin gerekmez). Oynatıcı sadece bu klasördekileri çalar.
- **Android Auto:** Playlistler / Tümü / Müzikler / Favoriler ağacı (yalnızca ses). Telefonda *Android Auto → Geliştirici ayarları → Bilinmeyen kaynaklar* açık olmalı (Play Store dışı APK).
- **YouTube:** sunucu yalnızca linki tutar; cihaz senkron sırasında kendisi indirir (NewPipe Extractor). Video = sessiz H.264 + ayrı AAC, oynatırken birleştirilir. YouTube kullanım şartlarını göz önünde bulundur; yalnızca kişisel kullanım içindir.

## Derleme
    export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
    ./gradlew :app:assembleDebug                     # debug
    ./gradlew :app:assembleRelease -Pmekronize.baseUrl=https://api.alanadin.com   # imzalı release

Çıktı: `app/build/outputs/apk/release/app-release.apk` (~4,7 MB). İmza bilgisi `keystore.properties` + `mekronize-release.jks` dosyalarındadır;
**bunları yedekle ve paylaşma** (aynı anahtarla imzalanmayan APK, mevcut kurulumun üstüne güncelleme olarak kurulmaz).
Sunucu adresi uygulamada senkron ekranından da değiştirilebilir.

## Yerel test
Emülatörde bilgisayardaki sunucu `http://10.0.2.2:PORT` adresindedir (bu adrese HTTP izni `network_security_config.xml` içinde tanımlı; diğer her şey HTTPS).

## Eski cihaz notu (HTTPS sertifikası)
Android 7.0 ve altı, Let's Encrypt'in yeni kök sertifikasına (ISRG Root X1) güvenmez. cPanel'de **AutoSSL sağlayıcısını "cPanel (Sectigo)"** seç; Let's Encrypt kullanılırsa Android 5–7.0 cihazlar sunucuya bağlanamaz.
