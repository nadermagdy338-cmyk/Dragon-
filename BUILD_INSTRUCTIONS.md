# Build Instructions

## الإعدادات المطلوبة

### بيئة التطوير
1. **Android Studio Koala (2024.1.1)** أو أحدث
2. **JDK 17**: [تنزيل](https://adoptium.net/temurin/releases/?version=17)
3. **Android SDK 36**: مثبت عبر Android Studio SDK Manager
4. **NDK r29**: مثبت عبر SDK Manager (Tab SDK Tools) — نفس إصدار CI

### إعداد Rust
```bash
# تثبيت Rust
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh

# إضافة أهداف Android
rustup target add aarch64-linux-android armv7-linux-androideabi

# تثبيت cargo-ndk
cargo install cargo-ndk
```

### إعداد متغيرات البيئة
أضف إلى `~/.bashrc` أو `~/.zshrc`:
```bash
export ANDROID_HOME=~/Android/Sdk
export NDK_HOME=$ANDROID_HOME/ndk/26.3.11579262
export PATH=$PATH:$ANDROID_HOME/tools:$ANDROID_HOME/platform-tools
export PATH=$PATH:~/.cargo/bin
```

## خطوات البناء

### 1. بناء مكتبة Rust
```bash
cd manager/src/main/rust
cargo ndk -t armeabi-v7a -t arm64-v8a -o ../../../app/src/main/jniLibs build --release
```

### 2. بناء التطبيق
```bash
cd manager
./gradlew clean
./gradlew :app:assembleDebug
```

### 3. تثبيت على الجهاز
```bash
./gradlew :app:installDebug
```

### 4. بناء الإصدار النهائي (Release)
```bash
./gradlew :app:assembleRelease
```

## حل المشاكل الشائعة

### مشكلة: `java.lang.UnsatisfiedLinkError`
**الحل**: تأكد من وجود مكتبة `libmaxmanager_native.so` في `app/src/main/jniLibs/` وأن أسماء الدوال متطابقة مع كود Rust.

### مشكلة: `cargo-ndk` غير موجود
**الحل**: `cargo install cargo-ndk`

### مشكلة: Gradle build يستغرق وقتاً طويلاً
**الحل**: أضف في `gradle.properties`:
```
org.gradle.daemon=true
org.gradle.parallel=true
org.gradle.jvmargs=-Xmx4g
```

## بيئات البناء

| البيئة | الأمر | المخرجات |
|--------|-------|----------|
| Debug | `./gradlew :app:assembleDebug` | `app-debug.apk` |
| Release | `./gradlew :app:assembleRelease` | `app-release.apk` |
| Test | `./gradlew test` | تقارير الاختبارات |
| Lint | `./gradlew lint` | تقارير التدقيق |

## التحقق من البناء

بعد البناء الناجح، تحقق من:
1. ظهور شاشة Home مع البيانات الحية
2. تحديث CPU Load كل ثانيتين
3. ظهور التنبؤات (Predictions) بعد 10 ثوانٍ
4. عدم وجود أخطاء في Logcat

---

**دعم**: للاستفسارات، راجع [PROJECT_ARCHITECTURE.md](PROJECT_ARCHITECTURE.md)