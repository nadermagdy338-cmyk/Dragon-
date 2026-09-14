# Evidence — Max AI Control Plane

## التحقق التنفيذي (من الكود، لا من الذاكرة)

### 1. التوازن البنيوي
```
13/13 ملف Kotlin في maxai/ متوازن (واعٍ بالنصوص والتعليقات)
MaxManagerProps.kt + Ownership/Locks: متوازن
```

### 2. سلاسل التكامل المُثبتة
| السلسلة | المسار |
|---|---|
| القرار | `DeviceState → Objective → MinimalPlanner.plan → SafetyGovernor → arbiter → Backend` |
| التعلّم التجريبي | `MinimalPlanner.recordMeasuredOutcome → ControlOutcomeModel.observe` |
| التعلّم التنبؤي | `ResponseModel.observe` (بعد التنفيذ) + `.predict` (قبل) |
| الملكية | `ManualControlLocks.lockedKeys` → ترشيح → `arbiter.submit(baseline,restore)` |
| التفضيل | `Props.AI_OBJECTIVE → Objective.fromPreference → score/preferredDirection` |
| الأمان | `SafetyGovernor.preVeto` + `enforcePost(rollback)` — بلا `ProfileApplier` |

### 3. المُزَال (مفردات النصوص)
```
executeDecision ......... 0   (كان 8 أفعال عربية)
applyProfileIfChanged ... 0   (كان 6 مسارات ملفات)
am kill-all ............. 0   (فعل عدائي)
isPerformanceRaising .... 0
RecommendationTextClassifier 0
lastAppliedProfile ...... 0   (حالة ميتة)
contextDataSource ....... 0
"رفع التردد" ............ 0
```
**163 سطراً** من المفردات الخشنة أُزيلت.

### 4. نتائج المحاكاة الرقمية (بلا JDK)
| الاختبار | النتيجة |
|---|---|
| التمييز الشرطي (تنبؤ) | 1.06× → **4.0×** |
| خطأ التنبؤ الكلي | 0.0893 → **0.0356** |
| نقل المعرفة بين الأجهزة | ✅ 0.333 = 0.333 |
| الصدق (بلا نصاب) | ✅ `None` قبل 4 عينات |
| الثبات العددي | ✅ بلا انفجار |

### 5. القيود البيئية
- **لا JDK/Java** ⇒ `:app:compileDebugKotlin` غير منفَّذ
- **لا Rust toolchain** ⇒ `cargo build` غير منفَّذ
- التحقق كان **ساكناً + محاكاة رقمية** فقط
