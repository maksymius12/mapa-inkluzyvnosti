package com.academy.mapainkluzyvnosti.ui.screens.photoupload

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.academy.mapainkluzyvnosti.data.model.CheckResult
import com.academy.mapainkluzyvnosti.data.remote.dto.AnalyzePlacePhotoResponse
import com.academy.mapainkluzyvnosti.data.remote.toUserMessage
import com.academy.mapainkluzyvnosti.data.repository.CheckRepository
import com.academy.mapainkluzyvnosti.data.repository.PlaceRepository
import com.academy.mapainkluzyvnosti.data.repository.UploadedPhoto
import com.academy.mapainkluzyvnosti.ui.state.CurrentUserStore
import com.academy.mapainkluzyvnosti.ui.state.DemoModeStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Один із 7 критеріїв: ключ колонки checks і підпис. */
enum class PhotoCriterion(val key: String, val label: String) {
    RAMP("ramp", "Пандус / вхід без сходинок"),
    DOOR_WIDTH("door_width", "Ширина дверей (≥80 см)"),
    THRESHOLD("threshold", "Немає порогів і перепадів"),
    ELEVATOR("elevator", "Ліфт (за потреби)"),
    TOILET("toilet", "Доступний туалет"),
    TACTILE("tactile", "Тактильні позначки"),
    STAFF("staff_assistance", "Допомога персоналу");

    fun of(check: CheckResult): Boolean = when (this) {
        RAMP -> check.ramp
        DOOR_WIDTH -> check.doorWidth
        THRESHOLD -> check.threshold
        ELEVATOR -> check.elevator
        TOILET -> check.toilet
        TACTILE -> check.tactile
        STAFF -> check.staffAssistance
    }
}

enum class AiVerdict { YES, NO, NOT_VISIBLE }

/** Пропозиція ШІ по критерію: що він бачить, чому, і яке значення буде після «Замінити дані». */
data class CriterionProposal(
    val criterion: PhotoCriterion,
    val verdict: AiVerdict,
    val reason: String,
    /** Поточне значення з останньої перевірки людиною; null — перевірки ще не було. */
    val current: Boolean?,
    /** Після «Замінити дані»: ШІ може лише знизити («Так» → «Ні»), але ніколи не підвищити. */
    val proposed: Boolean?
)

enum class PhotoStep { PICK, UPLOADING, ANALYZING, REVIEW, ANALYSIS_FAILED, SAVING, DONE }

data class PhotoUploadUiState(
    /** Перемикач угорі: з ШІ-аналізом (за замовчуванням) чи просто завантажити фото. */
    val withAi: Boolean = true,
    val step: PhotoStep = PhotoStep.PICK,
    val analysis: AnalyzePlacePhotoResponse? = null,
    val proposals: List<CriterionProposal> = emptyList(),
    /** Чи є перевірка людиною, яку ШІ може «знизити». Без неї «Замінити дані» недоступне. */
    val hasHumanCheck: Boolean = false,
    /** Скільки критеріїв зміниться («Так» → «Ні») після «Замінити дані». */
    val loweredCount: Int = 0,
    val doneMessage: String? = null,
    val errorMessage: String? = null,
    val showDemoGate: Boolean = false
)

/**
 * Фото місця з ШІ-аналізом або без. З аналізом людина бачить опис і пропозиції по 7 критеріях і обирає:
 * «Замінити дані й завантажити фото» (нова перевірка зі зниженими значеннями + статус ШІ) або
 * «Лише завантажити фото» (дані місця не змінюються). Принцип проєкту: ШІ може лише знизити статус.
 */
class PhotoUploadViewModel(
    private val placeId: String,
    private val placeRepository: PlaceRepository,
    private val checkRepository: CheckRepository,
    private val currentUserStore: CurrentUserStore,
    private val demoModeStore: DemoModeStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(PhotoUploadUiState())
    val uiState: StateFlow<PhotoUploadUiState> = _uiState.asStateFlow()

    /** Завантажений файл, для якого ще не створено рядок (щоб видалити його при скасуванні). */
    private var pending: UploadedPhoto? = null
    private var humanCheck: CheckResult? = null

    init {
        viewModelScope.launch {
            humanCheck = runCatching { placeRepository.getPlaceById(placeId)?.checks }.getOrNull()
            _uiState.update { it.copy(hasHumanCheck = humanCheck != null) }
        }
    }

    fun dismissDemoGate() = _uiState.update { it.copy(showDemoGate = false) }

    fun setWithAi(enabled: Boolean) {
        if (_uiState.value.step == PhotoStep.PICK) _uiState.update { it.copy(withAi = enabled, errorMessage = null) }
    }

    fun onPhotoPicked(bytes: ByteArray) {
        if (demoModeStore.isDemoMode.value) {
            _uiState.update { it.copy(showDemoGate = true) }
            return
        }
        val userId = currentUserStore.user.value?.id ?: return
        val withAi = _uiState.value.withAi
        viewModelScope.launch {
            _uiState.update { it.copy(step = PhotoStep.UPLOADING, errorMessage = null) }
            val uploaded = runCatching { placeRepository.uploadPhotoFile(placeId, userId, bytes, "jpg") }.getOrElse { e ->
                _uiState.update { it.copy(step = PhotoStep.PICK, errorMessage = e.toUserMessage("Не вдалося завантажити фото. Спробуйте ще раз")) }
                return@launch
            }
            pending = uploaded
            if (!withAi) {
                save(analysis = null, applyData = false)
                return@launch
            }
            _uiState.update { it.copy(step = PhotoStep.ANALYZING) }
            runCatching { placeRepository.analyzePhoto(uploaded.url) }
                .onSuccess { analysis ->
                    if (!analysis.isPlacePhoto) {
                        placeRepository.deletePhotoFile(uploaded.path)
                        pending = null
                        _uiState.update {
                            it.copy(
                                step = PhotoStep.PICK,
                                errorMessage = "Фото не прийнято: ${analysis.title}. Сфотографуйте саме це місце, " +
                                    "без мемів, скриншотів і картинок з інтернету"
                            )
                        }
                    } else {
                        val proposals = proposals(analysis)
                        _uiState.update {
                            it.copy(
                                step = PhotoStep.REVIEW,
                                analysis = analysis,
                                proposals = proposals,
                                loweredCount = proposals.count { p -> p.current == true && p.proposed == false }
                            )
                        }
                    }
                }
                .onFailure { _uiState.update { it.copy(step = PhotoStep.ANALYSIS_FAILED) } }
        }
    }

    /** «Замінити дані й завантажити фото». */
    fun applyAndSave() {
        if (_uiState.value.step == PhotoStep.REVIEW) save(_uiState.value.analysis, applyData = true)
    }

    /** «Лише завантажити фото» (і після невдалого аналізу — «Завантажити без аналізу»). */
    fun saveOnly() {
        val step = _uiState.value.step
        if (step == PhotoStep.REVIEW || step == PhotoStep.ANALYSIS_FAILED) save(_uiState.value.analysis, applyData = false)
    }

    /** Скасувати після аналізу: файл прибираємо, нічого не зберігаємо. */
    fun discard() {
        val file = pending ?: return
        pending = null
        viewModelScope.launch { placeRepository.deletePhotoFile(file.path) }
        _uiState.update { it.copy(step = PhotoStep.PICK, analysis = null, proposals = emptyList(), errorMessage = null) }
    }

    private fun save(analysis: AnalyzePlacePhotoResponse?, applyData: Boolean) {
        val file = pending ?: return
        val userId = currentUserStore.user.value?.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(step = PhotoStep.SAVING, errorMessage = null) }
            runCatching {
                // Спершу фото (зі статусом ШІ лише при «Замінити дані»), потім перевірка — тригер checks
                // поєднує її зі статусом останнього фото й бере гірший.
                placeRepository.savePhoto(placeId, userId, file.url, analysis, applyStatus = applyData)
                pending = null
                val base = humanCheck
                if (applyData && base != null) {
                    val lowered = lowered(base, _uiState.value.proposals)
                    checkRepository.submitCheck(placeId, userId, lowered, "За ШІ-аналізом фото (лише зниження)")
                }
            }.onSuccess {
                val message = when {
                    !applyData -> "Фото завантажено. Дані місця не змінювались."
                    humanCheck == null -> "Фото завантажено. Статус ШІ враховано; критерії змінить перевірка людиною."
                    else -> "Фото завантажено, дані оновлено за ШІ-аналізом."
                }
                _uiState.update { it.copy(step = PhotoStep.DONE, doneMessage = message) }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(step = if (analysis != null) PhotoStep.REVIEW else PhotoStep.PICK, errorMessage = e.toUserMessage("Не вдалося зберегти фото. Спробуйте ще раз"))
                }
            }
        }
    }

    private fun proposals(analysis: AnalyzePlacePhotoResponse): List<CriterionProposal> =
        PhotoCriterion.entries.map { criterion ->
            val raw = analysis.criteria[criterion.key]
            val verdict = when (raw?.verdict) {
                "yes" -> AiVerdict.YES
                "no" -> AiVerdict.NO
                else -> AiVerdict.NOT_VISIBLE
            }
            val current = humanCheck?.let(criterion::of)
            CriterionProposal(
                criterion = criterion,
                verdict = verdict,
                reason = raw?.reason.orEmpty(),
                current = current,
                // «Ні» від ШІ знижує; «Так» і «не видно» лишають значення людини як є.
                proposed = current?.let { if (verdict == AiVerdict.NO) false else it }
            )
        }

    private fun lowered(base: CheckResult, proposals: List<CriterionProposal>): CheckResult {
        fun value(c: PhotoCriterion) = proposals.firstOrNull { it.criterion == c }?.proposed ?: c.of(base)
        return base.copy(
            ramp = value(PhotoCriterion.RAMP),
            doorWidth = value(PhotoCriterion.DOOR_WIDTH),
            threshold = value(PhotoCriterion.THRESHOLD),
            elevator = value(PhotoCriterion.ELEVATOR),
            toilet = value(PhotoCriterion.TOILET),
            tactile = value(PhotoCriterion.TACTILE),
            staffAssistance = value(PhotoCriterion.STAFF),
            checkedAt = null
        )
    }

    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    override fun onCleared() {
        // Пішли з екрана, нічого не обравши, — файл без рядка не лишаємо.
        pending?.let { file -> kotlinx.coroutines.GlobalScope.launch { placeRepository.deletePhotoFile(file.path) } }
    }
}
