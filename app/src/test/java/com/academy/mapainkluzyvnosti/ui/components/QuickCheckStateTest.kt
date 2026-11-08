package com.academy.mapainkluzyvnosti.ui.components

import com.academy.mapainkluzyvnosti.data.model.AccessStatus
import com.academy.mapainkluzyvnosti.data.model.CheckResult
import com.academy.mapainkluzyvnosti.domain.usecase.ComputeAccessStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuickCheckStateTest {

    private fun answered(vararg yes: AccessCriterion) =
        AccessCriterion.entries.fold(QuickCheckState()) { state, c -> state.with(c, c in yes) }

    @Test
    fun nothingSelectedByDefault() {
        val state = QuickCheckState()
        AccessCriterion.entries.forEach { assertNull(state.value(it)) }
        assertEquals(AccessCriterion.entries.map { it.def }, state.unanswered)
        assertNull(state.toCheckResult())
    }

    @Test
    fun partiallyAnsweredIsNotSaved() {
        val state = QuickCheckState().with(AccessCriterion.RAMP, true).with(AccessCriterion.DOOR_WIDTH, false)
        assertNull(state.toCheckResult())
        assertEquals(5, state.unanswered.size)
    }

    @Test
    fun eachCriterionMapsToItsOwnColumn() {
        AccessCriterion.entries.forEach { criterion ->
            val result = answered(criterion).toCheckResult()!!
            AccessCriterion.entries.forEach { other -> assertEquals(other == criterion, other.of(result)) }
        }
    }

    @Test
    fun statusIsComputedAsBefore() {
        val critical = AccessCriterion.entries.filter { it.critical }.toTypedArray()
        assertEquals(AccessStatus.ACCESSIBLE, ComputeAccessStatus(answered(*AccessCriterion.entries.toTypedArray()).toCheckResult()))
        assertEquals(AccessStatus.PARTIAL, ComputeAccessStatus(answered(*critical).toCheckResult()))
        assertEquals(AccessStatus.BARRIER, ComputeAccessStatus(answered(AccessCriterion.RAMP, AccessCriterion.DOOR_WIDTH).toCheckResult()))
    }

    @Test
    fun parkingWithoutAnswerIsSavedAsNo() {
        assertEquals(false, answered().toCheckResult()!!.accessibleParking)
        assertEquals(true, answered().copy(accessibleParking = true).toCheckResult()!!.accessibleParking)
    }

    @Test
    fun prefillRoundTripsLatestCheck() {
        val check = CheckResult(
            ramp = true, doorWidth = false, threshold = true, elevator = false,
            toilet = true, tactile = false, staffAssistance = true, accessibleParking = true
        )
        assertEquals(check, QuickCheckState.from(check).toCheckResult())
    }

    @Test
    fun stopFormNeedsAllNineAnswers() {
        val partial = QuickCheckState.forStop(null).with(StopCriteria.criteria.first(), true)
        assertNull(partial.toStopAnswers())
        assertNull(partial.toCheckResult())
        assertEquals(8, partial.unanswered.size)
        val full = StopCriteria.criteria.fold(QuickCheckState.forStop(null)) { s, c -> s.with(c, c.critical) }
        assertEquals(StopCriteria.criteria.associate { it.key to it.critical }, full.toStopAnswers())
    }

    @Test
    fun stopCriteriaCriticalArePlatformHeightAndObstacleFree() {
        assertEquals(listOf("platform_height", "obstacle_free"), StopCriteria.criteria.filter { it.critical }.map { it.key })
        assertEquals(9, StopCriteria.criteria.size)
        assertEquals(StopCriteria, criteriaFor(com.academy.mapainkluzyvnosti.data.model.PlaceCategory.TRANSIT))
        assertEquals(EntranceCriteria, criteriaFor(com.academy.mapainkluzyvnosti.data.model.PlaceCategory.CAFE))
    }
}
