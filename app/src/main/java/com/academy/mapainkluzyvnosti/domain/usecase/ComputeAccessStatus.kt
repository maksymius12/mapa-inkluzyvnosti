package com.academy.mapainkluzyvnosti.domain.usecase

import com.academy.mapainkluzyvnosti.data.model.AccessStatus
import com.academy.mapainkluzyvnosti.data.model.CheckResult

/**
 * Обчислює [AccessStatus] за чек-листом та (опційно) поєднує його з AI-пропозицією
 * зі статусу фото. AI ніколи не підвищує статус — лише людина через повторний чек-лист.
 */
object ComputeAccessStatus {

    operator fun invoke(checks: CheckResult?): AccessStatus {
        if (checks == null) return AccessStatus.UNVERIFIED
        val criticalPassed = checks.ramp && checks.doorWidth && checks.threshold
        if (!criticalPassed) return AccessStatus.BARRIER
        val allPassed = criticalPassed && checks.elevator && checks.toilet && checks.tactile && checks.staffAssistance
        return if (allPassed) AccessStatus.ACCESSIBLE else AccessStatus.PARTIAL
    }

    fun combineWithAiStatus(computed: AccessStatus, aiSuggestedStatus: AccessStatus?): AccessStatus {
        if (aiSuggestedStatus == null) return computed
        return if (severity(aiSuggestedStatus) > severity(computed)) aiSuggestedStatus else computed
    }

    private fun severity(status: AccessStatus): Int = when (status) {
        AccessStatus.BARRIER -> 3
        AccessStatus.PARTIAL -> 2
        AccessStatus.ACCESSIBLE -> 1
        AccessStatus.UNVERIFIED -> 0
    }
}
