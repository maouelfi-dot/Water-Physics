# Water Physics v8 (Fabric 1.21.1)

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

## Baru di v4
- Ombak jauh lebih tinggi dan tajam (8 gelombang, puncak memutih seperti buih), kolam kecil pun bergelombang (`minDepthFactor`).
- Lapisan air lebih pekat (`meshAlpha` 215) sehingga air vanila di bawahnya tidak terlihat lagi.
- Aliran air nyata (server): air mengalir lebih cepat dan mencari turunan lebih jauh. Atur `fastFlow`, `flowTickRate` (vanilla 5), `flowDistance` (vanilla 4).
- Config lama otomatis dibuat ulang (configVersion 3). Hapus `config/oceanphysics.json` kalau mau reset.

## Baru di v5
- Permukaan air digambar per blok dengan sudut dirata-ratakan: tidak ada retakan/petak terang, rata dengan dinding kolam, air mengalir ikut mulus.
- Splash/riak sekarang kelihatan: lembah menggelap, puncak memutih, riak dari lompatan/mob lebih kuat + semprotan percikan.
- Kolam tertutup tidak menggembung (ombak dikurangi otomatis kalau air di sekitar sedikit); laut terbuka bergelombang penuh.
- Saat mesh aktif muncul teks di layar "Water Physics: mesh air aktif"; kalau error muncul pesan "mesh air MATI" di chat.

## Baru di v6: air tumpah (simulasi cairan partikel)
- **Sneak + klik kanan ember air** pada sebuah blok: air TIDAK jadi blok sumber, tapi tumpah jadi cairan partikel yang jatuh, memercik, menyebar, mengalir turun tangga/tepi blok, menyatu jadi genangan halus yang menipis di tepi, lalu meresap. Klik kanan tanpa Sneak = ember vanila. Ember jadi kosong.
- Fisika: gravitasi, tolakan/tekanan antar partikel, viskositas, kohesi (menggumpal), tabrakan dengan blok, didorong pemain/mob yang lewat, menyatu ke kolam (memicu riak).
- Ini simulasi di client (seperti Physics Mod): pemain lain di server tidak melihat cairannya.
- Config: `spill`, `spillAlways` (true = tanpa Sneak), `spillMax` (kecil = ringan), `spillPerBucket`, `spillLifeSeconds`.

## Baru di v7: fisika air TOTAL (mengganti sistem air vanila, server)
- Logika aliran air vanila diganti `RealFluid`: air = massa terbatas (1/8 blok per sel). Air jatuh ke bawah sampai penuh, lalu meratakan tinggi dengan tetangga (bejana berhubungan). Volume tetap, tidak ada sumber tak terbatas.
- Ember air = 8 satuan: di tanah rata menyebar jadi genangan tipis; di cekungan mengisi dan rata. Lubang di danau terisi dari sekitarnya dan permukaannya turun sedikit.
- Disimpan sebagai blok air vanila (level 0..7), jadi tersimpan di dunia, arus/entity/render vanila tetap jalan.
- WAJIB backup world dulu. Matikan dengan `realFluid`: false. Kalau lag turunkan `realFluidPerTick`.

## Baru di v8: badai, arus, kapal goyang, silau
- **Badai**: hujan/petir dunia membuat ombak makin tinggi (sampai ~3x saat petir), lebih kasar, air jadi kelabu gelap. Server dan client sama (dari cuaca dunia). `stormWaves`, `stormWaveBoost`, `stormChoppy`, `stormTint`.
- **Arus ombak nyata**: semua benda (pemain, mob, item, blok jatuh, TNT, kereta, kapal) diseret kecepatan partikel air (orbital + drift Stokes), bukan cuma naik-turun. Dekat pantai ombak menghempas ke daratan lalu menarik balik (undertow). `waterCurrent`, `currentStrength`, `shoreSurge`, `surgeStrength`, `stokesDrift`.
- **Di dalam badan ombak**: benda yang naik ke puncak ombak (di atas air vanila) tetap dianggap di air, jadi tidak jatuh menembus. `extendedWet`.
- **Kapal**: naik-turun dari 5 titik + goyang pegas-peredam (oleng, memantul, tenang). `boatRig`, `boatRockStiffness`, `boatRockDamping`.
- **Erosi pantai**: saat badai kuat, ombak menyapu blok rapuh di tepi air (obor, bunga, tebu, redstone); item-nya hanyut. Blok padat aman. `washAway`, `washStormMin`, `washPerSecond`.
- **Silau** matahari/bulan di air, semprotan di puncak ombak saat badai, hujan lebih deras = riak lebih banyak. `glare`, `glareStrength`, `spray`.
- `gradle.properties` dibuat ulang (sebelumnya kosong, build akan gagal). Jika versi Fabric API tidak ditemukan, ganti `fabric_version`.

## Baru di v8.1: pohon roboh
- Tebang satu batang kayu pohon (harus ada daun di sekitarnya, rumah kayu aman): batang di atasnya jadi balok jatuh yang rebah menjauhi kamu, makin tinggi makin jauh. Daun ikut berjatuhan (dibatasi), sisanya luruh biasa. Mendarat jadi balok lagi atau jadi item. Jatuh ke air ikut fisika ombak.
- Config: `treeFall`, `treeMaxLogs` (160), `treeMaxLeaves` (70, kecil = ringan untuk HP).
