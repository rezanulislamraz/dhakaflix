# Dhaka Flix (Android phone + Android TV)

## APK বানানোর সহজ উপায় (Android Studio ছাড়া, ফ্রি)
1. github.com এ একটা ফ্রি অ্যাকাউন্ট খুলুন → New repository (নাম: dhakaflix, Public বা Private)।
2. এই zip এর ভেতরের সব ফাইল/ফোল্ডার repository তে আপলোড করুন
   (Add file → Upload files). `.github` ফোল্ডারটি অবশ্যই যেন যায়।
   আপলোডে `.github` না গেলে: Add file → Create new file → নাম দিন
   `.github/workflows/build.yml` এবং এই zip এর ভেতরের build.yml এর লেখা paste করুন।
3. Actions ট্যাব → "Build Dhaka Flix APK" (নিজে থেকেই চলবে, না চললে Run workflow)।
4. ৫-১০ মিনিট পর রান শেষ হলে নিচে Artifacts এ **DhakaFlix-APK** ডাউনলোড করুন → zip খুললে app-debug.apk।
5. APK ফোনে/টিভিতে ইনস্টল করুন (Unknown sources চালু করতে হবে)।
   টিভির জন্য: pendrive এ কপি করুন বা "Send Files to TV" অ্যাপ ব্যবহার করুন।

## Android Studio দিয়ে
File → Open → এই ফোল্ডার → Build → Build APK(s).

## প্রথমবার চালানোর পর
- Settings → TMDb API key বসান (themoviedb.org থেকে ফ্রি) → পোস্টার আসবে।
- Settings → Test connection (ftp://172.16.50.4) → Rebuild index (সার্চ ও Recently Added এর জন্য)।
- সার্ভারের ঠিকানা বদলাতে হলে Settings থেকে বদলান, বা Data.kt এর DEFAULT_SERVER।
- Google Sans ফন্ট দিতে চাইলে Theme.kt এর AppFont এর কমেন্ট দেখুন।
