package com.academy.mapainkluzyvnosti.ui.state

import com.academy.mapainkluzyvnosti.data.model.AppUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Поточний користувач сесії — заповнюється після входу, читається екранами, що залежать від ролі. */
class CurrentUserStore {

    private val _user = MutableStateFlow<AppUser?>(null)
    val user: StateFlow<AppUser?> = _user.asStateFlow()

    fun set(user: AppUser) {
        _user.value = user
    }

    fun clear() {
        _user.value = null
    }
}
