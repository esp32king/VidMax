# GodxShadow Downloader (v6.0)

Purani **NEON Caller ID (REAPER)** app ko video-downloader me convert kiya gaya hai.
Splash + neon glass popup wahi rakhe gaye, saari **calling / caller-ID APIs hata di** gayi.

## Kya-kya diya gaya hai
- **Splash same** — skull mark waisa hi; ab **"GodxShadow" text shine (shimmer)** karta hai (top wordmark + "Created by GodxShadow" dono).
- **Share to app** — YouTube / Instagram / Facebook / Snapchat / kisi bhi app/site se video ya link **Share → GodxShadow** karo. App poori nahi khulti; screen ke **right side me ek halka floating popup** aata hai jise **drag karke move** kar sakte ho, upar-right me **close (X)** icon.
- **Quality popup** — link par quality poochta hai: **144p → 4K (2160p)** + **Audio (MP3)**. (YouTube 4K bhi.)
- **Circular download bubble** — quality chunte hi popup chhota hokar **circle** ban jata hai: **beech me %**, **corner me size (12MB/50MB)**. Tap karne par **Pause/Resume + Open-app + Cancel** icons. **Complete hote hi gayab** ho jata hai.
- **VidMate-jaisi Home** — saari files: **Downloading / Completed** tabs, progress bar, pause/resume, open, delete.
- **Glass + neon CSS** poore app me.
- Files yahan save hoti hain: `Android/data/com.example.myapplication/files/Movies/GodxShadow/` + gallery me bhi copy (Movies/GodxShadow, audio Music/GodxShadow).

## Engine
Real extraction/download **yt-dlp + ffmpeg** (youtubedl-android) se — 1000+ sites, DASH video+audio ko mp4 me merge, pause = process kill, resume = `-c` continue.

## Install / permissions
1. Phone par APK install karo (same signing key, purane app ke upar update ho jayega).
2. Pehli baar **"Display over other apps"** allow karna hoga (popup ke liye) + notifications.
3. **Pehle download par engine apna chhota core (python/yt-dlp) setup karta hai** — thoda ruko.

## Rebuild
```
bash /home/user/GodxDownloader/build.sh
# output: /home/user/output/GodxShadow-Downloader-v6.0.apk
```

## Zaroori notes
- Sirf **arm64-v8a** (aaj-kal ke saare phone). Purane 32-bit phone ke liye `app/build.gradle` me `abiFilters` me `'armeabi-v7a'` add karo.
- Main ye APK yahan chala kar test nahi kar sakta — **asli device par test zaroori** hai. yt-dlp samay-samay par update chahta hai (Home/settings se later add kiya ja sakta hai) kyunki sites badalti rehti hain.
- Video download karna kai platforms ki Terms of Service ke against ho sakta hai — sirf apne/authorised content ke liye use karein.
