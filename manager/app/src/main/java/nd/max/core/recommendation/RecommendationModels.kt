/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.recommendation

/**
 * أفعال التوصيات القابلة للتنفيذ — الفئات المشتركة بين المصنِّف
 * (RecommendationTextClassifier) ومحرك MAX AI (MaxAiEngine).
 *
 * كانت داخل SmartRecommendationEngine قبل توحيد المحركات؛ انتقلت هنا
 * لأن المصنّف يحتاجها ومحرك MAX AI يستهلك مخرجاته، بينما المحرك القديم
 * نفسه حُذف (منطقه الحي انتقل إلى MaxAiEngine).
 */
sealed class RecommendationAction {
    object ApplyPerformanceProfile : RecommendationAction()
    object ApplyPowerSaveProfile : RecommendationAction()
    object ApplyBalancedProfile : RecommendationAction()
    object ReduceCpuFrequency : RecommendationAction()
    object IncreaseCpuFrequency : RecommendationAction()
    object EnableGamingMode : RecommendationAction()
    object DisableGamingMode : RecommendationAction()
    object SuggestCharging : RecommendationAction()
    object SuggestClosingApps : RecommendationAction()
    object RebootDevice : RecommendationAction()
    object NoAction : RecommendationAction()
}

enum class RecommendationCategory {
    PERFORMANCE, BATTERY, THERMAL, SECURITY, GENERAL
}
