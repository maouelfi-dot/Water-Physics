# Ocean Physics (Fabric 1.21.1)

## Build dari HP (tanpa PC)
1. Buat repo baru di GitHub (lewat browser), upload SEMUA isi folder ini (termasuk folder `.github`).
2. Tab **Actions** -> workflow `build` -> tunggu hijau -> download artifact `oceanphysics-jar`.
3. Taruh `.jar` di folder mods. Butuh Fabric API. Java 21.
4. Kalau build merah: kirim log error ke Claude.

## Fitur
- Ombak prosedural tidak berulang (8 gelombang + modulasi), mengecil di air dangkal.
- Kapal: naik-turun fisik (client + server), meluncur di lereng ombak, miring (visual).
- Pemain berenang di permukaan ikut terangkat. Item jatuh dan pelampung pancing ikut ombak.
- Busa di puncak ombak dan tepi pantai, buih di belakang kapal.
- Permukaan air bergelombang (mesh dinamis) di sekitar kamera, otomatis mati jika error.
- Blok terapung: pegang **Float Wand** (tongkat + lapis), klik kanan blok yang menyentuh air; klik kanan lagi untuk mengembalikan.
  Resep: lapis di atas, 2 stick di bawahnya.

## Config: `config/oceanphysics.json`
- `waveSize`: SMALL / MEDIUM / LARGE
- `waveSpeed`, `windAngleDegrees`, `seed` (samakan di server dan semua pemain)
- `boatFollowStrength` (0 mati, 1 normal), `boatTilt`, `boatSlide`
- `renderWaterMesh`, `meshRadius` (kecil = ringan), `meshCell` (besar = ringan), `meshWithShaders`
- `foam`, `foamSamples` (turunkan jika berat), `foamRadius`, `foamIntensity`
- `swimBobbing`, `itemBobbing`, `floatingBlocks`

## Jika ada masalah
- Berat/FPS turun: `meshRadius` 24, `meshCell` 3, `foamSamples` 4.
- Kapal terlalu kaku/lembek: ubah `boatFollowStrength`.
- Crash: kirim `logs/latest.log` ke Claude.
