/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * MaxFx — **العقد المزدوج**: غلافُ مؤثّر AIDL فوق النواة نفسها التي يخدمها `AELI`.
 *
 * ## لماذا هذا الملفّ موجود (والسبب مُقاس لا مُخمَّن)
 *
 * `audioserver` في Android 12+ لا يحمّل مكتبات المؤثّرات القديمة بنفسه: يسأل خدمة المؤثّرات
 * AIDL (`android.hardware.audio.effect.IFactory`)، ومصنّعُها (`EffectFactory.cpp`) يستخرج من
 * المكتبة **ثلاثة رموز بأسمائها**:
 *
 *   createEffect · queryEffect · destroyEffect
 *
 * وتوقيعاتها الثلاثة منقولةٌ حرفيًّا من `hardware/interfaces/audio/aidl/default/include/
 * effect-impl/EffectTypes.h` (Apache-2.0، بنسبة الفضل):
 *
 *   typedef binder_exception_t (*EffectCreateFunctor)(const AudioUuid*,
 *       std::shared_ptr<IEffect>*);
 *   typedef binder_exception_t (*EffectDestroyFunctor)(const std::shared_ptr<IEffect>&);
 *   typedef binder_exception_t (*EffectQueryFunctor)(const AudioUuid*, Descriptor*);
 *
 * ومكتبةٌ لا تُصدِّرها تُرفض في المصنع برسالة `create (0), query (0), or destroy (0) not
 * exist in library` — **وهذا هو عطب «صفر تغيير في الصوت»** بعد أن صار المؤثّر المركَّب على
 * جهاز المالك (Android 16) يُطلب بعقد AIDL لا بعقد `AELI`.
 *
 * ## وما تغيّر في النواة: لا شيء
 *
 * المعالجة كلّها في `maxfx_dsp` (نقيّة، مُقاسة على المضيف بـ٢٠٣ اختبارات)، وقناة التحكّم
 * تبقى **الخاصيّات** (`persist.audio.maxfx.*`) كما في غلاف `AELI` — فالمؤثّر هنا غلافٌ
 * آخر حول النواة نفسها، لا نسخةٌ ثانية منها. و`AELI` **تبقى مُصدَّرة في المكتبة نفسها**
 * فيعمل الجهاز على الجيلين (≤11/HIDL و12+/AIDL) بلا تفريعين.
 *
 * ## وحدودٌ مُعلنة في هذا الغلاف
 *
 * ① **يستقبل `float` حصرًا.** `EffectImpl` المرجعيّ يرفض غير `FLOAT_32_BIT`
 *    (`commonMustBe32BitsFloat`)، والتحويل من/إلى `s16` مسؤوليّة الإطار — فلا نُكرّره.
 * ② **قناتان أو واحدة.** وما عداهما يُمرَّر كما هو (قبولٌ بلا أثر مُعلن) لأنّ النواة
 *    مصمَّمة لأحاديّ/ستيريو.
 * ③ **`setParameter` يقبل ولا يُطبِّق**: ما لا تعرفه الأداة من مسوّدات الإطار يُخزَّن ويُقرّ
 *    بالنجاح (لا فشلٌ كاذب يُسقط السلسلة)، والتحكّم الفعليّ من الخاصيّات. وهذا فرقٌ مقصود
 *    عن `EffectImpl` المرجعيّ الذي يرفض ما لا يعرفه.
 */
#include <aidl/android/hardware/audio/effect/BnEffect.h>

#include <fmq/AidlMessageQueue.h>
#include <fmq/EventFlag.h>

#include <algorithm>
#include <atomic>
#include <condition_variable>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <memory>
#include <mutex>
#include <optional>
#include <thread>
#include <vector>

#include "maxfx_dsp.h"
#include "maxfx_effect.h"

namespace aidl::android::hardware::audio::effect {

namespace {

/* أعلام حالة المؤثّر — القيم من `system/media/audio/include/system/audio_effects/
 * aidl_effects_utils.h` (سطر ٥٤): الإصدار ١ من HAL يستعمل البتّ 0، والإصداران ٢+ يستعملان
 * البتّ 11 (`kEventFlagDataMqNotEmpty`) — ونحن نُعلن الإصدار ١، فنُبقي البتّ 0 حرفيًّا. */
constexpr uint32_t kMaxFxEventFlagNotEmpty = 0x1;

/* نجاحُ عقد المؤثّر القديم: `effect_param_t::status == 0` و`STATUS_OK == 0`. */
constexpr int32_t kStatusOk = 0;

/* حساب عدد القنوات من قناع `AudioChannelLayout` — بديلٌ محلّيّ لـ`getChannelCount` من
 * `libaudioaidlcommon` (ليست في الـNDK). والتمثيل **قناعُ بتات** لا عدّاد. */
size_t channelCountOf(const ::aidl::android::media::audio::common::AudioChannelLayout& mask) {
    if (mask.getTag() !=
        ::aidl::android::media::audio::common::AudioChannelLayout::layoutMask) {
        return 0;
    }
    const int32_t bits = mask.get<::aidl::android::media::audio::common::AudioChannelLayout::layoutMask>();
    if (bits <= 0) return 0;
    return (size_t)__builtin_popcount((uint32_t)bits);
}

bool isFloat32(const ::aidl::android::media::audio::common::AudioFormatDescription& f) {
    return f.pcm == ::aidl::android::media::audio::common::PcmType::FLOAT_32_BIT;
}

/* نصّ UUID إلى `AudioUuid` — وتحويله من `effect_uuid_t` المحلّيّ (١٦ بايت، نفس الترتيب)، فلا
 * يُكتب مُحلِّلٌ ثانٍ للصيغة. */
::aidl::android::media::audio::common::AudioUuid audioUuidFrom(const effect_uuid_t& id) {
    ::aidl::android::media::audio::common::AudioUuid out;
    out.timeLow = (int32_t)id.timeLow;
    out.timeMid = (int32_t)id.timeMid;
    out.timeHiAndVersion = (int32_t)id.timeHiAndVersion;
    out.clockSeq = (int32_t)id.clockSeq;
    out.node.assign(6, 0);
    for (int i = 0; i < 6; i++) out.node[(size_t)i] = (int8_t)id.node[i];
    return out;
}

::aidl::android::media::audio::common::AudioUuid audioUuidOf(const char* text) {
    effect_uuid_t parsed;
    memset(&parsed, 0, sizeof(parsed));
    /* فشل التحليل يعطي UUID صفريًّا — وهو نوعٌ لا يُطابق مؤثّرًا على أي جهاز (لا انفجار). */
    (void)maxfx_uuid_parse(text, &parsed);
    return audioUuidFrom(parsed);
}

bool uuidIs(const ::aidl::android::media::audio::common::AudioUuid& lhs,
            const ::aidl::android::media::audio::common::AudioUuid& rhs) {
    return lhs.timeLow == rhs.timeLow && lhs.timeMid == rhs.timeMid &&
           lhs.timeHiAndVersion == rhs.timeHiAndVersion && lhs.clockSeq == rhs.clockSeq &&
           lhs.node == rhs.node;
}

/* الوصف الوحيد للمؤثّر — **مصدره `maxfx_descriptor` نفسها** التي يخدم منها `AELI`،
 * فلا يختلف وصفٌ عن وصفٍ بين العقدين. */
void fillDescriptor(Descriptor* desc) {
    effect_descriptor_t legacy;
    maxfx_descriptor(&legacy);

    Descriptor out;
    out.common.id.type = audioUuidFrom(legacy.type);
    out.common.id.uuid = audioUuidFrom(legacy.uuid);
    out.common.flags.type = Flags::Type::INSERT;
    out.common.flags.insert = Flags::Insert::LAST;
    out.common.flags.volume = Flags::Volume::NONE;
    out.common.cpuLoad = (int32_t)legacy.cpuLoad;
    out.common.memoryUsage = (int32_t)legacy.memoryUsage;
    out.common.name = legacy.name;
    out.common.implementor = legacy.implementor;
    /* `capability` تُترك فارغة عن قصد: نوعٌ مُخصَّص لا تنطبق عليه مدىات الأنواع المعروفة
     * (`Equalizer`, `BassBoost`, …) — والإطار لا يطلبها لنوعٍ لا يعرفه. */
    *desc = std::move(out);
}

}  // namespace

/**
 * وحدة مؤثّر AIDL: مقبضٌ واحد لكل `createEffect`، يملك نوى FMQ الثلاث (الحالة · الدخل ·
 * الخرج) وخيط معالجة واحدًا، ويُحيل كل معالجة إلى `maxfx_dsp` نفسها.
 */
class MaxFxEffect final : public BnEffect {
  public:
    using StatusMQ = ::android::AidlMessageQueue<
            IEffect::Status, ::aidl::android::hardware::common::fmq::SynchronizedReadWrite>;
    using DataMQ = ::android::AidlMessageQueue<
            float, ::aidl::android::hardware::common::fmq::SynchronizedReadWrite>;

    MaxFxEffect() = default;
    ~MaxFxEffect() override { stopWorker(); }

    ndk::ScopedAStatus open(const Parameter::Common& common,
                            const std::optional<Parameter::Specific>& specific,
                            IEffect::OpenEffectReturn* ret) override {
        (void)specific; /* لا معامل نوعٍ أوّليّ عندنا: التحكّم من الخاصيّات. */
        if (common.input.base.format.pcm != common.output.base.format.pcm ||
            !isFloat32(common.input.base.format) || !isFloat32(common.output.base.format)) {
            return ndk::ScopedAStatus::fromExceptionCodeWithMessage(
                    EX_ILLEGAL_ARGUMENT, "dataMustBe32BitsFloat");
        }

        std::lock_guard<std::mutex> lg(mImplMutex);
        /* نداءٌ ثانٍ بلا `close` ليس خطأً — الإطار قد يُعيد المحاولة، والحالة قائمة. */
        if (mState != State::INIT) return ndk::ScopedAStatus::ok();
        if (ret == nullptr) {
            return ndk::ScopedAStatus::fromExceptionCodeWithMessage(EX_NULL_POINTER, "nullReturn");
        }

        const size_t inChannels = channelCountOf(common.input.base.channelMask);
        const size_t outChannels = channelCountOf(common.output.base.channelMask);
        if (inChannels == 0 || outChannels == 0) {
            return ndk::ScopedAStatus::fromExceptionCodeWithMessage(EX_ILLEGAL_ARGUMENT,
                                                                   "illegalChannelCount");
        }
        if (common.input.frameCount <= 0 || common.output.frameCount <= 0) {
            return ndk::ScopedAStatus::fromExceptionCodeWithMessage(EX_ILLEGAL_ARGUMENT,
                                                                   "illegalFrameCount");
        }

        mCommon = common;
        mInputChannels = inChannels;
        mOutputChannels = outChannels;
        /* الحجم بوحدات `float` كما في `EffectContext` المرجعيّ: إطارات × قنوات × 4 بايت ÷ 4. */
        const size_t inFloats = (size_t)common.input.frameCount * inChannels;
        const size_t outFloats = (size_t)common.output.frameCount * outChannels;

        /* الحالة وحدها تحتاج كلمة علم الحدث (تنتظرها المعالجة)، وقطعتان كحدٍّ أدنى. */
        mStatusMQ = std::make_shared<StatusMQ>(2 /* depth */, true /* configureEventFlagWord */);
        mInputMQ = std::make_shared<DataMQ>(inFloats);
        mOutputMQ = std::make_shared<DataMQ>(outFloats);
        if (!mStatusMQ->isValid() || !mInputMQ->isValid() || !mOutputMQ->isValid()) {
            releaseQueuesLocked();
            return ndk::ScopedAStatus::fromExceptionCodeWithMessage(EX_NULL_POINTER, "invalidFmq");
        }

        ::android::hardware::EventFlag* group = nullptr;
        if (::android::hardware::EventFlag::createEventFlag(mStatusMQ->getEventFlagWord(),
                                                            &group) != ::android::OK ||
            group == nullptr) {
            releaseQueuesLocked();
            return ndk::ScopedAStatus::fromExceptionCodeWithMessage(EX_ILLEGAL_STATE,
                                                                   "eventFlagFailed");
        }
        mEfGroup = group;

        /* النواة تُهيَّأ على تيرة الدخل، والإعداد يُطبَّق من الخاصيّات حين يبدأ المعالجة. */
        maxfx_config_default(&mCfg);
        maxfx_state_init(&mDsp, (float)(common.input.base.sampleRate > 0
                                                ? common.input.base.sampleRate
                                                : 48000));
        mPropsSinceRefresh = 0;
        mPropsIntervalFrames = std::max<int64_t>(1, common.input.base.sampleRate / 4);
        mWorkBufferFloats = std::max(inFloats, outFloats);
        mWorkBuffer.assign(mWorkBufferFloats, 0.0f);

        ret->statusMQ = mStatusMQ->dupeDesc();
        ret->inputDataMQ = mInputMQ->dupeDesc();
        ret->outputDataMQ = mOutputMQ->dupeDesc();

        mState = State::IDLE;
        return ndk::ScopedAStatus::ok();
    }

    ndk::ScopedAStatus close() override {
        std::unique_lock<std::mutex> lk(mImplMutex);
        if (mState == State::INIT) return ndk::ScopedAStatus::ok();
        mState = State::INIT;
        mEnabled = false;
        /* الإيقاف **قبل** تحرير المقابض: الخيط يقرأ منها، وتحريرها تحته سباق. */
        stopWorkerLocked(lk);
        releaseQueuesLocked();
        return ndk::ScopedAStatus::ok();
    }

    ndk::ScopedAStatus getDescriptor(Descriptor* desc) override {
        if (desc == nullptr) {
            return ndk::ScopedAStatus::fromExceptionCodeWithMessage(EX_NULL_POINTER, "nullDesc");
        }
        fillDescriptor(desc);
        return ndk::ScopedAStatus::ok();
    }

    ndk::ScopedAStatus getState(State* state) override {
        if (state == nullptr) {
            return ndk::ScopedAStatus::fromExceptionCodeWithMessage(EX_NULL_POINTER, "nullState");
        }
        std::lock_guard<std::mutex> lg(mImplMutex);
        *state = mState;
        return ndk::ScopedAStatus::ok();
    }

    ndk::ScopedAStatus command(CommandId id) override {
        std::unique_lock<std::mutex> lk(mImplMutex);
        if (mState == State::INIT) {
            return ndk::ScopedAStatus::fromExceptionCodeWithMessage(EX_ILLEGAL_STATE,
                                                                   "instanceNotOpen");
        }
        switch (id) {
            case CommandId::START:
                if (mState == State::PROCESSING) return ndk::ScopedAStatus::ok();
                mEnabled = true;
                refreshFromPropertiesLocked(true);
                mState = State::PROCESSING;
                wakeLocked();
                startWorkerLocked();
                break;
            case CommandId::STOP:
                if (mState == State::IDLE) return ndk::ScopedAStatus::ok();
                mEnabled = false;
                mState = State::IDLE;
                wakeLocked();
                stopWorkerLocked(lk);
                break;
            case CommandId::RESET:
                mEnabled = false;
                mState = State::IDLE;
                wakeLocked();
                stopWorkerLocked(lk);
                maxfx_state_reset(&mDsp);
                /* ما تراكم في دخل FMQ لم يُعالَج — يُطرح صراحةً فلا يُعالج متأخّرًا بضبطٍ جديد. */
                drainInputLocked();
                break;
            default:
                /* أوامر `VENDOR_COMMAND_*` ليست عندنا: الفشل هنا **مُعلن** لا مُهلَّل عليه. */
                return ndk::ScopedAStatus::fromExceptionCodeWithMessage(EX_ILLEGAL_ARGUMENT,
                                                                       "CommandIdNotSupported");
        }
        return ndk::ScopedAStatus::ok();
    }

    ndk::ScopedAStatus setParameter(const Parameter& param) override {
        std::lock_guard<std::mutex> lg(mImplMutex);
        switch (param.getTag()) {
            case Parameter::common:
                /* تغيير `common` أثناء المعالجة لا يُعيد بناء المقابض (وإلا ضاع مقبض الإطار):
                 * يُخزَّن، ويُطبَّق عند `open` التالي — وهذا فرقٌ عن الإصدار ٢+ الذي له
                 * `reopen`. */
                mCommon = param.get<Parameter::common>();
                break;
            case Parameter::deviceDescription:
                mDeviceDescription = param.get<Parameter::deviceDescription>();
                break;
            case Parameter::mode:
                mAudioMode = param.get<Parameter::mode>();
                break;
            case Parameter::source:
                mAudioSource = param.get<Parameter::source>();
                break;
            case Parameter::volumeStereo:
                mVolumeStereo = param.get<Parameter::volumeStereo>();
                break;
            default:
                /* نوعٌ فريد (`specific`) لا نُترجمه: التحكّم من الخاصيّات. ويُقبل بلا أثر
                 * حتى لا يُسقط الإطار سلسلةً حيّة بسبب مسوّدةٍ لا تخصّنا. */
                break;
        }
        return ndk::ScopedAStatus::ok();
    }

    ndk::ScopedAStatus getParameter(const Parameter::Id& id, Parameter* param) override {
        if (param == nullptr) {
            return ndk::ScopedAStatus::fromExceptionCodeWithMessage(EX_NULL_POINTER, "nullParam");
        }
        std::lock_guard<std::mutex> lg(mImplMutex);
        if (id.getTag() != Parameter::Id::commonTag) {
            return ndk::ScopedAStatus::fromExceptionCodeWithMessage(EX_ILLEGAL_ARGUMENT,
                                                                   "tagNotSupported");
        }
        switch (id.get<Parameter::Id::commonTag>()) {
            case Parameter::common:
                param->set<Parameter::common>(mCommon);
                break;
            case Parameter::deviceDescription:
                param->set<Parameter::deviceDescription>(mDeviceDescription);
                break;
            case Parameter::mode:
                param->set<Parameter::mode>(mAudioMode);
                break;
            case Parameter::source:
                param->set<Parameter::source>(mAudioSource);
                break;
            case Parameter::volumeStereo:
                param->set<Parameter::volumeStereo>(mVolumeStereo);
                break;
            default:
                return ndk::ScopedAStatus::fromExceptionCodeWithMessage(EX_ILLEGAL_ARGUMENT,
                                                                       "commonParamNotSupported");
        }
        return ndk::ScopedAStatus::ok();
    }

  private:
    /* ── القراءة الدوريّة للخاصيّات — نفس مقايضة غلاف `AELI` (ربع ثانية كحدٍّ أقصى) ── */

    void refreshFromPropertiesLocked(bool force) {
        mPropsSinceRefresh += (int64_t)mLastFrames;
        if (!force && mLastFrames > 0 && mPropsSinceRefresh < mPropsIntervalFrames) return;
        mPropsSinceRefresh = 0;
        int changed = 0;
        const int count = maxfx_param_count();
        for (int i = 0; i < count; i++) {
            const maxfx_param_def_t* p = maxfx_param_at(i);
            char key[96];
            char value[96];
            snprintf(key, sizeof(key), "%s%s", MAXFX_PROP_PREFIX, p->key);
            if (!maxfx_prop_get(key, value, sizeof(value))) continue; /* غياب ≠ صفر (ADR-07) */
            if (p->is_int) {
                int32_t raw = (int32_t)strtol(value, nullptr, 10);
                changed |= maxfx_config_set_param(&mCfg, p->id, &raw, sizeof(raw));
            } else {
                float raw = strtof(value, nullptr);
                changed |= maxfx_config_set_param(&mCfg, p->id, &raw, sizeof(raw));
            }
        }
        if (changed) maxfx_apply_config(&mDsp, &mCfg);
    }

    /* ── FMQ ── */

    void releaseQueuesLocked() {
        if (mEfGroup != nullptr) {
            ::android::hardware::EventFlag::deleteEventFlag(&mEfGroup);
            mEfGroup = nullptr;
        }
        mStatusMQ.reset();
        mInputMQ.reset();
        mOutputMQ.reset();
        mWorkBuffer.clear();
        mWorkBufferFloats = 0;
    }

    void drainInputLocked() {
        if (!mInputMQ) return;
        const size_t available = (size_t)mInputMQ->availableToRead();
        if (available == 0 || mWorkBuffer.empty()) return;
        const size_t take = std::min(available, mWorkBufferFloats);
        (void)mInputMQ->read(mWorkBuffer.data(), take);
    }

    void wakeLocked() {
        if (mEfGroup != nullptr) (void)mEfGroup->wake(kMaxFxEventFlagNotEmpty);
    }

    /* ── خيط المعالجة ── */

    void startWorkerLocked() {
        if (mWorker.joinable()) return;
        mWorkerExit = false;
        mWorker = std::thread([this] { workerLoop(); });
    }

    /* `lk` هو قفل الحالة نفسه — يُحرَّر أثناء `join` ثمّ يُعاد قفله، فلا خيطٌ ينتظر قفلًا
     * محتجزًا ولا قفلٌ يُحرَّر مرّتين. وهذا سببُ أخذ `unique_lock` لا `lock_guard`. */
    void stopWorkerLocked(std::unique_lock<std::mutex>& lk) {
        if (!mWorker.joinable()) return;
        {
            std::lock_guard<std::mutex> lg(mWorkerMutex);
            mWorkerExit = true;
        }
        mWorkerCv.notify_all();
        wakeLocked(); /* والخيط قد يكون نائمًا على علم الحدث لا على الشرط */
        lk.unlock();
        mWorker.join();
        lk.lock();
    }

    /* بدون امتلاك `mImplMutex` — يُنادى من المُفكِّر وحده. */
    void stopWorker() {
        std::unique_lock<std::mutex> lk(mImplMutex);
        stopWorkerLocked(lk);
    }

    void workerLoop() {
        while (true) {
            {
                std::lock_guard<std::mutex> lg(mWorkerMutex);
                if (mWorkerExit) return;
            }
            if (mEfGroup == nullptr) return;
            uint32_t efState = 0;
            /* بلا مهلة (`0` = انتظار أبديّ) مع إعادة المحاولة على اليقظة الكاذبة — كما في
             * `EffectImpl::process` المرجعيّ. */
            const ::android::status_t status =
                    mEfGroup->wait(kMaxFxEventFlagNotEmpty, &efState, 0, true);
            {
                std::lock_guard<std::mutex> lg(mWorkerMutex);
                if (mWorkerExit) return;
            }
            if (status != ::android::OK) continue;
            processOnce();
        }
    }

    void processOnce() {
        std::lock_guard<std::mutex> lg(mImplMutex);
        if (!mEnabled || mState != State::PROCESSING) return;
        if (!mStatusMQ || !mInputMQ || !mOutputMQ) return;

        const int32_t frames = (int32_t)std::min((int64_t)mInputMQ->availableToRead() /
                                                         (int64_t)mInputChannels,
                                                 (int64_t)mOutputMQ->availableToWrite() /
                                                         (int64_t)mOutputChannels);
        if (frames <= 0) return;

        const size_t inFloats = (size_t)frames * mInputChannels;
        const size_t outFloats = (size_t)frames * mOutputChannels;
        if (inFloats > mWorkBufferFloats || outFloats > mWorkBufferFloats) return;

        const size_t read = (size_t)mInputMQ->read(mWorkBuffer.data(), inFloats);
        if (read != inFloats) {
            /* قراءةٌ ناقصة (سباق مع الإطار): يُبلَّغ بها ولا تُخترع عيّنات. */
            IEffect::Status failed{kStatusOk, (int32_t)read, 0};
            (void)mStatusMQ->writeBlocking(&failed, 1);
            return;
        }

        mLastFrames = frames;
        refreshFromPropertiesLocked(false);

        int32_t producedFloats = 0;
        if (mInputChannels == mOutputChannels && mInputChannels <= MAXFX_MAX_CHANNELS) {
            /* معالجة في الموضع: فكّ تشابك إلى خطة، ثمّ إعادة تشابك — والقنوات تقرأ حالةً
             * مستقلّة لكلٍّ منها (عقد `maxfx_dsp`). */
            const size_t blockMax = 256;
            for (size_t done = 0; done < (size_t)frames; done += blockMax) {
                const size_t block = std::min(blockMax, (size_t)frames - done);
                float left[blockMax], right[blockMax];
                for (size_t i = 0; i < block; i++) {
                    const float* src = mWorkBuffer.data() + (done + i) * mInputChannels;
                    left[i] = src[0];
                    right[i] = (mInputChannels == 2) ? src[1] : src[0];
                }
                maxfx_process(&mDsp, left, right, (int)block);
                for (size_t i = 0; i < block; i++) {
                    float* dst = mWorkBuffer.data() + (done + i) * mOutputChannels;
                    dst[0] = left[i];
                    if (mOutputChannels == 2) dst[1] = right[i];
                }
            }
            producedFloats = (int32_t)outFloats;
        } else {
            /* أكثر من قناتين: **مرورٌ حرفيّ** لا معالجة مشوَّهة (مُعلن في رأس الملفّ). */
            producedFloats = (mOutputChannels == mInputChannels)
                                     ? (int32_t)inFloats
                                     : (int32_t)std::min(inFloats, outFloats);
        }

        if (producedFloats > 0) {
            (void)mOutputMQ->write(mWorkBuffer.data(), (size_t)producedFloats);
        }
        IEffect::Status ok{kStatusOk, (int32_t)read, producedFloats};
        (void)mStatusMQ->writeBlocking(&ok, 1);
    }

    /* ── الحالة (محميّة بـ`mImplMutex`) ── */
    std::mutex mImplMutex;
    State mState = State::INIT;
    bool mEnabled = false;
    Parameter::Common mCommon{};
    std::vector<::aidl::android::media::audio::common::AudioDeviceDescription> mDeviceDescription{};
    ::aidl::android::media::audio::common::AudioMode mAudioMode =
            ::aidl::android::media::audio::common::AudioMode::SYS_RESERVED_INVALID;
    ::aidl::android::media::audio::common::AudioSource mAudioSource =
            ::aidl::android::media::audio::common::AudioSource::SYS_RESERVED_INVALID;
    Parameter::VolumeStereo mVolumeStereo{};
    size_t mInputChannels = 0;
    size_t mOutputChannels = 0;

    std::shared_ptr<StatusMQ> mStatusMQ;
    std::shared_ptr<DataMQ> mInputMQ;
    std::shared_ptr<DataMQ> mOutputMQ;
    std::vector<float> mWorkBuffer;
    size_t mWorkBufferFloats = 0;
    ::android::hardware::EventFlag* mEfGroup = nullptr;

    /* النواة — الملكيّة لهذا المقبض وحده، والوصول كلّه تحت `mImplMutex`. */
    maxfx_state_t mDsp{};
    maxfx_config_t mCfg{};
    int64_t mLastFrames = 0;
    int64_t mPropsSinceRefresh = 0;
    int64_t mPropsIntervalFrames = 12000;

    /* خيط المعالجة */
    std::thread mWorker;
    std::mutex mWorkerMutex;
    std::condition_variable mWorkerCv;
    bool mWorkerExit = false;
};

}  // namespace aidl::android::hardware::audio::effect

/* ── الرموز الثلاثة التي يبحث عنها مصنع AIDL — بأسمائها وتوقيعاتها ───────────────────── */

extern "C" binder_exception_t createEffect(
        const ::aidl::android::media::audio::common::AudioUuid* uuid,
        std::shared_ptr<::aidl::android::hardware::audio::effect::IEffect>* instanceSpp) {
    if (uuid == nullptr || instanceSpp == nullptr) return EX_ILLEGAL_ARGUMENT;
    const auto implUuid = ::aidl::android::hardware::audio::effect::audioUuidOf(MAXFX_IMPL_UUID_STR);
    if (!::aidl::android::hardware::audio::effect::uuidIs(*uuid, implUuid)) {
        return EX_ILLEGAL_ARGUMENT;
    }
    *instanceSpp = ndk::SharedRefBase::make<::aidl::android::hardware::audio::effect::MaxFxEffect>();
    return *instanceSpp ? EX_NONE : EX_TRANSACTION_FAILED;
}

extern "C" binder_exception_t queryEffect(
        const ::aidl::android::media::audio::common::AudioUuid* uuid,
        ::aidl::android::hardware::audio::effect::Descriptor* desc) {
    if (uuid == nullptr || desc == nullptr) return EX_ILLEGAL_ARGUMENT;
    const auto implUuid = ::aidl::android::hardware::audio::effect::audioUuidOf(MAXFX_IMPL_UUID_STR);
    if (!::aidl::android::hardware::audio::effect::uuidIs(*uuid, implUuid)) {
        return EX_ILLEGAL_ARGUMENT;
    }
    ::aidl::android::hardware::audio::effect::fillDescriptor(desc);
    return EX_NONE;
}

extern "C" binder_exception_t destroyEffect(
        const std::shared_ptr<::aidl::android::hardware::audio::effect::IEffect>& instanceSp) {
    if (!instanceSp) return EX_ILLEGAL_ARGUMENT;
    /* شبكة الأمان: `EffectImpl` المرجعيّ يشترط `INIT` قبل الإصدار، ونحن نُوقف ونُغلق أولًا
     * ثمّ نُسقط المرجع — فمؤثّرٌ في `PROCESSING` لا يُترك بخيطٍ حيّ. */
    instanceSp->command(::aidl::android::hardware::audio::effect::CommandId::STOP);
    instanceSp->close();
    const_cast<std::shared_ptr<::aidl::android::hardware::audio::effect::IEffect>&>(instanceSp)
            .reset();
    return EX_NONE;
}
