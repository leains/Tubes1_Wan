# TUBES STIMA 1
INI Adalah Tugas Besar IF2211 - Mata Kuliah Strategi Algoritma 
Kelompok Wan 


## Penjelasan Singkat Algoritma Greedy
### Bot Utama (13524119)
Dua strategi greedy berbasis ekspansi:
- **Greedy Tile Terjauh** — Soldier cari tile belum diwarnai yang paling jauh, gerak ke sana. Bertahan di area sampai sebagian besar tile diwarnai sebelum ekspansi
- **Greedy Chase Musuh** — Mopper kejar tile cat musuh terdekat dan langsung attack. Sebagian Mopper (ID genap) jadi messenger: keliling ke tower sekutu dan kirim sinyal bahaya kalau ada ruin yang terancam. 

### Bot Alternatif 1 (13524146)
Bot ini mengimplementasikan empat strategi greedy yang bekerja secara bersamaan:
- **Greedy Move** — tiap robot nilai 8 arah, pilih skor tertinggi berdasarkan tile sekitar 
- **Greedy Build Tower** — Soldier langsung bangun tower begitu ketemu ruin, tipe ditentukan dari kondisi chips dan jumlah tower saat itu
- **Greedy Spawn** — Tower spawn tipe robot yang rasionya paling jauh dari target (60% Soldier, 30% Mopper, 10% Splasher)
- **Greedy Best Splasher** - Menentukan timing waktu dan lokasi untuk melakukan splash yang paling tepat. 

### Bot Alternatif 2 (13524115)
Strategi greedy berbasis komunikasi terdistribusi:
- **Greedy Pemilihan Ruin** — Soldier pilih ruin dengan skor `1000/(jarak+1) − 0.5×jarak_ke_tower`, hindari ruin dekat tower musuh (-100)
- **Greedy Weighted Attack** — Tower serang target dengan skor `1000/(HP+1) + bonus_tipe` (Splasher +500, Mopper +300, tower musuh +100)
- **Greedy Intel Broadcast** — Begitu temukan tower musuh atau ruin kosong, langsung broadcast ke seluruh tim tanpa menunggu


## Requirement dan Instalasi
1. Gradlew 
(https://docs.gradle.org/current/userguide/installation.html)
2. JDK Ver.21 
(https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html)

## HOW TO START?
### Useful Commands
- `./gradlew build`
    Compiles your player
- `./gradlew run`
    Runs a game with the settings in gradle.properties
- `./gradlew update`
    Update configurations for the latest version -- run this often
- `./gradlew zipForSubmit`
    Create a submittable zip file
- `./gradlew tasks`
    See what else you can do!
### Memulai 
```bash
cd STIMA-BATTLE
```
```bash
./gradlew build
```
```bash
cd client
```
```bash
Jalankan Stima Battle Client.exe
Masukkan dua bot yang akan diadu pada tab runner
Tekan run
```


## Author
- Ega Luthfi Rais | 13524115
- Nathanael Shane Bennet | 13524119
- Leonardus Brain Fatolosja | 13524146
