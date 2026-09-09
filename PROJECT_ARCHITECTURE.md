# MaxManager - Clean Architecture Implementation

## نظرة عامة

هذا المشروع يعتمد على **Clean Architecture** مع الفصل الصارم بين طبقات Data و Domain و Presentation، مع تكامل Native عبر JNI/NDK باستخدام Rust و C.

## هيكل المشروع

```
app/
├── src/main/java/nd/max/
│   ├── core/                    # البنية التحتية المشتركة
│   │   ├── di/                  # وحدات Hilt/Dagger
│   │   │   ├── AppModule.kt
│   │   │   └── DataModule.kt
│   │   ├── hardware/            # بيانات الأجهزة
│   │   │   ├── HardwareDataSource.kt
│   │   │   └── HardwareControlArbiter.kt
│   │   ├── jni/                 # ربط JNI
│   │   │   └── PredictorBridge.kt
│   │   └── threading/           # إدارة الخيوط
│   │       └── DispatcherProvider.kt
│   │
│   ├── domain/                  # طبقة الأعمال (مستقلة عن Android)
│   │   ├── models/              # نماذج المجال
│   │   │   └── LoadPrediction.kt
│   │   ├── repositories/        # واجهات المستودعات
│   │   │   └── IPredictorRepository.kt
│   │   └── usecases/            # حالات الاستخدام
│   │       └── PredictLoadUseCase.kt
│   │
│   ├── data/                    # طبقة البيانات
│   │   ├── datasources/         # مصادر البيانات
│   │   │   └── HardwareDataSourceImpl.kt
│   │   └── repositories/        # تنفيذ المستودعات
│   │       └── PredictorRepositoryImplV2.kt
│   │
│   └── presentation/            # طبقة الواجهات
│       ├── ui/                  # الشاشات
│       │   ├── home/HomeScreen.kt
│       │   └── dashboard/DashboardScreen.kt
│       └── viewmodels/          # ViewModels
│           ├── MainViewModel.kt
│           └── DashboardViewModel.kt
│
├── src/main/rust/               # كود Rust
│   ├── Cargo.toml
│   └── src/lib.rs
│
└── src/main/cpp/                # كود C/C++ (اختياري)
```

## التدفق البياني للبيانات

```
[Hardware] → [DataSource] → [Repository] → [UseCase] → [ViewModel] → [UI]
     ↑             ↓              ↓            ↓           ↓
   (Native)      (JNI)       (Domain)     (Business)  (State)
```

## التبعيات الرئيسية

| المكتبة | الإصدار | الاستخدام |
|---------|---------|----------|
| Kotlin | 2.3.10 | لغة التطوير الأساسية |
| Compose BOM | 2025.01.00 | UI الحديثة |
| Hilt | 2.51.1 | حقن التبعيات |
| Coroutines | 1.9.0 | إدارة الخيوط |
| Rust JNI | 0.21.1 | ربط Rust مع Java |
| rustfft | 6.2.0 | تحليل فورييه الحراري |

## كيفية البناء

### 1. تثبيت المتطلبات
- **JDK 17** أو أحدث
- **Android SDK 37**
- **NDK 26** أو أحدث
- **Rust** مع `cargo-ndk`

### 2. تثبيت Rust NDK toolchain
```bash
rustup target add aarch64-linux-android armv7-linux-androideabi
cargo install cargo-ndk
```

### 3. بناء المشروع
```bash
cd manager
./gradlew :app:assembleDebug
```

### 4. تشغيل الاختبارات
```bash
./gradlew test
```

## وحدات الاختبار

- **Unit Tests**: اختبارات طبقة Domain و UseCases
- **Instrumentation Tests**: اختبارات UI و ViewModels
- **Native Tests**: اختبارات Rust عبر `cargo test`

## تحسينات الأداء

1. **معالجة الخلفية**: جميع العمليات الثقيلة تعمل على `Dispatchers.IO`
2. **إلغاء المهام**: إلغاء تلقائي عند تدمير ViewModel
3. **إدارة الذاكرة**: استخدام `WeakReference` في JNI حيثما أمكن
4. **التخزين المؤقت**: استخدام `Flow` مع `StateFlow` للتحديثات الفعالة

## الأمان

- جميع استدعاءات Shell تتم عبر **libsu** مع صلاحيات Root محدودة
- كود Rust معزول عن UI ولا يمكنه التسبب في تجميد
- التحقق من الصلاحيات قبل أي عملية نظام

---

**التاريخ:** 2026-09-06  
**الإصدار:** 1.0.0