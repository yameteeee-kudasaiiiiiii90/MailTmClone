# Mail.tm Native Android Clone

مشروع تطبيق أندرويد كامل (Kotlin + Jetpack Compose) مطابق لتصميم ووظائف خدمة البريد المؤقت **Mail.tm** مع دعم حفظ الحساب تلقائياً وإشعارات الخلفية عبر `WorkManager`.

## طريقتان لاستخراج ملف الـ APK:

### الطريقة الأولى: بدون تثبيت أي برامج (عبر GitHub Actions مجاناً)
المشروع مزود بملف بناء سحابي تلقائي (`.github/workflows/build-apk.yml`):
1. أنشئ مستودعاً (Repository) جديداً على حسابك في **GitHub**.
2. فك الضغط عن هذا الملف وارفع محتوياته بالكامل إلى المستودع.
3. ادخل إلى تبويب **Actions** في المستودع، ستجد عملية **Build Android APK** تعمل تلقائياً.
4. بعد انتهائها (تستغرق حوالي دقيقتين)، اضغط عليها وحمّل ملف **`MailTmClone-Debug-APK`** من قسم **Artifacts**.

### الطريقة الثانية: عبر برنامج Android Studio
1. فك الضغط عن الملف وافتح المجلد من داخل **Android Studio**.
2. انتظر انتهاء مزامنة **Gradle Sync**.
3. من القائمة العلوية اختر: **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
4. ستجد ملف الـ APK جاهزاً في المسار: `app/build/outputs/apk/debug/app-debug.apk`.
