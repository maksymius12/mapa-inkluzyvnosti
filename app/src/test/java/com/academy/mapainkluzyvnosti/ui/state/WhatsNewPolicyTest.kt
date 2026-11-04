package com.academy.mapainkluzyvnosti.ui.state

import org.junit.Assert.assertEquals
import org.junit.Test

class WhatsNewPolicyTest {

    private val releases = listOf(2, 3)

    @Test
    fun freshInstallOnlyRecordsVersion() {
        assertEquals(WhatsNewDecision.RecordOnly, WhatsNewPolicy.decide(null, 2, isFreshInstall = true, releaseCodes = releases))
    }

    @Test
    fun updateFromVersionWithoutKeyShowsCurrentRelease() {
        assertEquals(WhatsNewDecision.Show(listOf(2)), WhatsNewPolicy.decide(null, 2, isFreshInstall = false, releaseCodes = releases))
    }

    @Test
    fun updateShowsEveryNewerRelease() {
        assertEquals(WhatsNewDecision.Show(listOf(3, 2)), WhatsNewPolicy.decide(1, 3, isFreshInstall = false, releaseCodes = releases))
        assertEquals(WhatsNewDecision.Show(listOf(3)), WhatsNewPolicy.decide(2, 3, isFreshInstall = false, releaseCodes = releases))
    }

    @Test
    fun sameVersionDoesNothing() {
        assertEquals(WhatsNewDecision.Nothing, WhatsNewPolicy.decide(2, 2, isFreshInstall = false, releaseCodes = releases))
        assertEquals(WhatsNewDecision.Nothing, WhatsNewPolicy.decide(2, 2, isFreshInstall = true, releaseCodes = releases))
    }

    @Test
    fun downgradeDoesNothing() {
        assertEquals(WhatsNewDecision.Nothing, WhatsNewPolicy.decide(3, 2, isFreshInstall = false, releaseCodes = releases))
    }

    @Test
    fun updateWithoutContentOnlyRecords() {
        assertEquals(WhatsNewDecision.RecordOnly, WhatsNewPolicy.decide(3, 4, isFreshInstall = false, releaseCodes = releases))
    }
}
