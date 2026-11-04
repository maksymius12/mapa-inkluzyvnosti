package com.academy.mapainkluzyvnosti.ui.state

import android.content.Context
import com.academy.mapainkluzyvnosti.BuildConfig
import com.academy.mapainkluzyvnosti.data.local.AppPreferences
import com.academy.mapainkluzyvnosti.ui.screens.whatsnew.whatsNewReleases

/** Що робити з екраном «Що нового» при запуску (чиста логіка — покрита unit-тестом). */
sealed interface WhatsNewDecision {
    /** Вже бачив цю версію (або встановлено старішу збірку поверх) — нічого. */
    data object Nothing : WhatsNewDecision
    /** Свіже встановлення або оновлення без нових пунктів — лише запам'ятати версію. */
    data object RecordOnly : WhatsNewDecision
    /** Оновлення — показати релізи з versionCode у (бачена; поточна]. */
    data class Show(val versionCodes: List<Int>) : WhatsNewDecision
}

object WhatsNewPolicy {
    /**
     * @param lastSeen збережений last_seen_whats_new_version; null — ключа ще немає.
     * @param isFreshInstall застосунок щойно встановлено (а не оновлено).
     * @param releaseCodes versionCode-и, для яких є вміст у WhatsNewContent.
     */
    fun decide(lastSeen: Int?, current: Int, isFreshInstall: Boolean, releaseCodes: List<Int>): WhatsNewDecision {
        if (lastSeen == null && isFreshInstall) return WhatsNewDecision.RecordOnly
        if (lastSeen != null && lastSeen >= current) return WhatsNewDecision.Nothing
        // Ключа немає, але це оновлення (версія до появи екрана) — показуємо лише поточний реліз.
        val from = lastSeen ?: (current - 1)
        val codes = releaseCodes.filter { it in (from + 1)..current }.sortedDescending()
        return if (codes.isEmpty()) WhatsNewDecision.RecordOnly else WhatsNewDecision.Show(codes)
    }
}

/**
 * Екран «Що нового» — один раз для кожної версії. [checkOnLaunch] викликається при старті застосунку
 * (свіже встановлення одразу записує версію), а показує екран мапа — тобто вже після входу чи вибору гостя.
 */
class WhatsNewStore(private val context: Context) {

    private var pendingCodes: List<Int> = emptyList()

    fun checkOnLaunch() {
        val decision = WhatsNewPolicy.decide(
            lastSeen = AppPreferences.lastSeenWhatsNewVersion(context),
            current = BuildConfig.VERSION_CODE,
            isFreshInstall = isFreshInstall(),
            releaseCodes = whatsNewReleases.map { it.versionCode }
        )
        when (decision) {
            WhatsNewDecision.Nothing -> Unit
            WhatsNewDecision.RecordOnly -> markSeen()
            is WhatsNewDecision.Show -> pendingCodes = decision.versionCodes
        }
    }

    val hasPending: Boolean get() = pendingCodes.isNotEmpty()

    /** Релізи для показу; якщо процес перезапустився посеред показу — хоча б поточний. */
    fun releasesToShow() = whatsNewReleases
        .filter { it.versionCode in pendingCodes.ifEmpty { listOf(BuildConfig.VERSION_CODE) } }

    fun markSeen() {
        pendingCodes = emptyList()
        AppPreferences.setLastSeenWhatsNewVersion(context, BuildConfig.VERSION_CODE)
    }

    /**
     * Оновлення змінює lastUpdateTime, а свіже встановлення — ні. Прапорець привітання з попередньої
     * версії — додаткова ознака, що застосунком уже користувались.
     */
    private fun isFreshInstall(): Boolean {
        if (AppPreferences.isOnboardingSeen(context)) return false
        val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull() ?: return true
        return info.lastUpdateTime <= info.firstInstallTime
    }
}
