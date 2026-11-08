package com.academy.mapainkluzyvnosti.ui.screens.photoupload

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.academy.mapainkluzyvnosti.data.model.PlaceCategory
import com.academy.mapainkluzyvnosti.data.remote.dto.AnalyzePlacePhotoResponse
import com.academy.mapainkluzyvnosti.data.remote.toUserMessage
import com.academy.mapainkluzyvnosti.data.repository.CheckRepository
import com.academy.mapainkluzyvnosti.data.repository.PlaceRepository
import com.academy.mapainkluzyvnosti.data.repository.UploadedPhoto
import com.academy.mapainkluzyvnosti.ui.components.CriteriaKind
import com.academy.mapainkluzyvnosti.ui.components.CriteriaSet
import com.academy.mapainkluzyvnosti.ui.components.CriterionDef
import com.academy.mapainkluzyvnosti.ui.components.EntranceCriteria
import com.academy.mapainkluzyvnosti.ui.components.criteriaFor
import com.academy.mapainkluzyvnosti.ui.components.entranceCheckFrom
import com.academy.mapainkluzyvnosti.ui.components.toAnswers
import com.academy.mapainkluzyvnosti.ui.state.CurrentUserStore
import com.academy.mapainkluzyvnosti.ui.state.DemoModeStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AiVerdict { YES, NO, NOT_VISIBLE }

/** Пропозиція ШІ по критерію: що він бачить, чому, і яке значення буде після «Замінити дані». */
data class CriterionProposal(
    val criterion: CriterionDef,
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
    /** Критерії місця: 7 для входу, 9 для зупинки. */
    val set: CriteriaSet = EntranceCriteria,
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
 * Фото місця з ШІ-аналізом або без. З аналізом людина бачить опис і пропозиції по критеріях місця (7 для входу,
 * 9 для зупинки) і обирає:
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
    /** Остання перевірка людиною — відповіді за ключами критеріїв; null — перевірки ще не було. */
    private var humanCheck: Map<String, Boolean>? = null
    private var humanParking: Boolean = false
    private var category: PlaceCategory? = null

    init {
        viewModelScope.launch {
            val place = runCatching { placeRepository.getPlaceById(placeId) }.getOrNull()
            category = place?.category
            val set = place?.category?.let(::criteriaFor) ?: EntranceCriteria
            humanCheck = when (set.kind) {
                CriteriaKind.ENTRANCE -> place?.checks?.toAnswers()
                CriteriaKind.STOP -> place?.stopCheck?.answers?.takeIf { a -> set.criteria.all { it.key in a } }
            }
            humanParking = place?.checks?.accessibleParking == true
            _uiState.update { it.copy(set = set, hasHumanCheck = humanCheck != null) }
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
            // Для зупинки функція оцінює лише її видимі критерії (решта — «не видно»).
            val aiCategory = if (_uiState.value.set.kind == CriteriaKind.STOP) "transit" else null
            runCatching { placeRepository.analyzePhoto(uploaded.url, aiCategory) }
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
                    val comment = "За ШІ-аналізом фото (лише зниження)"
                    when (_uiState.value.set.kind) {
                        CriteriaKind.ENTRANCE -> entranceCheckFrom(lowered, humanParking)
                            ?.let { checkRepository.submitCheck(placeId, userId, it, comment) }
                        CriteriaKind.STOP -> checkRepository.submitStopCheck(placeId, userId, lowered, comment)
                    }
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
        _uiState.value.set.criteria.map { criterion ->
            val raw = analysis.criteria[criterion.key]
            val verdict = when (raw?.verdict) {
                "yes" -> AiVerdict.YES
                "no" -> AiVerdict.NO
                else -> AiVerdict.NOT_VISIBLE
            }
            val current = humanCheck?.get(criterion.key)
            CriterionProposal(
                criterion = criterion,
                verdict = verdict,
                reason = raw?.reason.orEmpty(),
                current = current,
                // «Ні» від ШІ знижує; «Так» і «не видно» лишають значення людини як є.
                proposed = current?.let { if (verdict == AiVerdict.NO) false else it }
            )
        }

    /** «Ні» від ШІ знижує значення людини; «Так» і «не видно» лишають як є. */
    private fun lowered(base: Map<String, Boolean>, proposals: List<CriterionProposal>): Map<String, Boolean> =
        base.mapValues { (key, value) -> proposals.firstOrNull { it.criterion.key == key }?.proposed ?: value }

    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    override fun onCleared() {
        // Пішли з екрана, нічого не обравши, — файл без рядка не лишаємо.
        pending?.let { file -> kotlinx.coroutines.GlobalScope.launch { placeRepository.deletePhotoFile(file.path) } }
    }
}
