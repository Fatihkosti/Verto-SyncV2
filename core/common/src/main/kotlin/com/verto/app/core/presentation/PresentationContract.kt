package com.verto.app.core.presentation

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** حالة مستقرة قابلة لإعادة العرض. */
interface UiState

/** مدخل واحد من الواجهة إلى ViewModel. */
interface UiEvent

/** حدث أحادي الاستهلاك مثل التنقل أو إغلاق شاشة. */
interface UiEffect

interface UiStateHolder<S : UiState> {
    val uiState: StateFlow<S>
}

interface UiEventHandler<E : UiEvent> {
    fun onEvent(event: E)
}

interface UiEffectSource<F : UiEffect> {
    val effects: SharedFlow<F>
}
