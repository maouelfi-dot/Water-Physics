# Water Physics v3 (Fabric 1.21.1)

## Build dari HP (tanpa PC)
1. Buat repo baru di GitHub (browser), upload SEMUA isi folder ini (termasuk folder `.github`).
2. Tab **Actions** -> workflow `build` -> tunggu hijau -> download artifact `oceanphysics-jar`.
3. Taruh `.jar` (yang tanpa `-sources`) di folder mods. Butuh Fabric API dan Java 21.
4. Kalau build merah: kirim log error ke Claude.

## Fisika yang bekerja
- **Ombak**: 6 gelombang sinus tidak berulang, mengecil di air dangkal. Dipakai bersama oleh server dan client.
- **Riak (persamaan gelombang 2D)**: percikan saat masuk air, wake di belakang pemain/mob/kapal/item, dan tetesan hujan menyebar, memantul di pantai, lalu meredam.
- **Daya apung**: pemain diam mengapung di garis air (tahan Sneak untuk menyelam). Kapal naik-turun mengikuti ombak dan miring.
- **Item**: item berat (besi, batu, emas, alat, armor, dll.) tenggelam dengan hambatan. Kayu, daun, wol, kapal mengapung dan hanyut oleh lereng ombak.
- **Pasir/kerikil jatuh** melambat di air.
- **Gelombang kejut**: benda atau mob besar yang jatuh ke air mendorong benda terapung di sekitarnya (momentum berkurang menurut jarak).
- **Mob** di permukaan ikut naik-turun dan hanyut.
- Lapisan air dinamis selalu di ATAS air vanilla (tidak ada kedip/z-fighting), aktif juga dengan shader Iris.

## Fisika item (baru di v3)
- Item yang dilempar atau di-drop **berputar jungkir balik di udara**, **memantul** saat jatuh keras, lalu **tergeletak diam** di tanah (item pipih rebah rata, blok duduk di tanah, tidak melayang).
- Item **hanya bisa diambil saat jongkok (Sneak) di dekatnya**. Matikan lewat `sneakPickup`: false.
- Riak air di dekat pemain ikut mempengaruhi kapal, pemain, item, dan mob (client).

## Config: `config/oceanphysics.json` (dibuat ulang otomatis untuk v2)
- `waveSize` SMALL/MEDIUM/LARGE, `waveSpeed`, `windAngleDegrees`, `seed` (samakan di server dan pemain)
- `itemPhysics`, `itemBounce`, `sneakPickup`
- `swellStrength` kekuatan gaya ombak, `playerFloat`, `itemSinking`, `fallingBlockDrag`, `splashShock`, `mobBobbing`
- `boatFollowStrength`, `boatTilt`, `boatSlide`
- `renderWaterMesh`, `meshRadius` (kecil = ringan), `meshCell` (2 = jauh lebih ringan), `meshAlpha`, `meshWithShaders`
- `ripples`, `rippleStrength`, `rippleSpeed`, `rippleDamping`, `rainRipples`
- `foam`, `foamSamples`, `foamRadius`, `foamIntensity`

## Untuk HP (Oppo A3s)
Default sudah ringan. Kalau FPS turun: `meshCell` 2, `meshRadius` 16, `foamSamples` 3, atau `renderWaterMesh` false (fisika tetap jalan).

## Jika ada masalah
Kirim `logs/latest.log`. Setiap fitur visual mematikan dirinya sendiri kalau error, game tidak crash.
