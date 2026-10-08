package com.academy.mapainkluzyvnosti.di

import com.academy.mapainkluzyvnosti.data.remote.NominatimApi
import com.academy.mapainkluzyvnosti.data.remote.OsrmApi
import com.academy.mapainkluzyvnosti.data.remote.OverpassApi
import com.academy.mapainkluzyvnosti.data.remote.createMapaSupabaseClient
import com.academy.mapainkluzyvnosti.data.remote.createOsmHttpClient
import com.academy.mapainkluzyvnosti.data.repository.AuthRepository
import com.academy.mapainkluzyvnosti.data.repository.CheckRepository
import com.academy.mapainkluzyvnosti.data.repository.FavoriteRepository
import com.academy.mapainkluzyvnosti.data.repository.PlaceRepository
import com.academy.mapainkluzyvnosti.data.repository.SosRepository
import com.academy.mapainkluzyvnosti.domain.usecase.BuildRoute
import com.academy.mapainkluzyvnosti.domain.usecase.ImportOsmPlaces
import com.academy.mapainkluzyvnosti.ui.screens.favorites.FavoritesViewModel
import com.academy.mapainkluzyvnosti.ui.screens.filters.FiltersViewModel
import com.academy.mapainkluzyvnosti.ui.screens.login.LoginViewModel
import com.academy.mapainkluzyvnosti.ui.screens.map.MapViewModel
import com.academy.mapainkluzyvnosti.ui.screens.photoupload.PhotoUploadViewModel
import com.academy.mapainkluzyvnosti.ui.screens.placedetail.PlaceDetailViewModel
import com.academy.mapainkluzyvnosti.ui.screens.notifications.NotificationsViewModel
import com.academy.mapainkluzyvnosti.ui.screens.profile.ProfileViewModel
import com.academy.mapainkluzyvnosti.ui.screens.settings.SettingsViewModel
import com.academy.mapainkluzyvnosti.ui.screens.quickcheck.QuickCheckViewModel
import com.academy.mapainkluzyvnosti.ui.screens.route.RouteViewModel
import com.academy.mapainkluzyvnosti.ui.screens.search.SearchViewModel
import com.academy.mapainkluzyvnosti.ui.screens.sos.SosRequestViewModel
import com.academy.mapainkluzyvnosti.ui.state.AppStartup
import com.academy.mapainkluzyvnosti.ui.state.CurrentUserStore
import com.academy.mapainkluzyvnosti.ui.state.DemoModeStore
import com.academy.mapainkluzyvnosti.ui.state.FavoriteRoutesStore
import com.academy.mapainkluzyvnosti.ui.state.MapFilterStore
import com.academy.mapainkluzyvnosti.ui.state.SettingsStore
import com.academy.mapainkluzyvnosti.ui.state.ThemeStore
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val networkModule = module {
    single { createMapaSupabaseClient() }
    single { createOsmHttpClient() }
    single { NominatimApi(get()) }
    single { OverpassApi(get()) }
    single { OsrmApi(get()) }
}

val repositoryModule = module {
    single { PlaceRepository(get()) }
    single { CheckRepository(get()) }
    single { AuthRepository(get()) }
    single { FavoriteRepository(get()) }
    single { SosRepository(get()) }
}

val useCaseModule = module {
    factory { ImportOsmPlaces(get(), get(), get()) }
    factory { BuildRoute(get()) }
}

val stateModule = module {
    single { SettingsStore(get()) }
    single { MapFilterStore(get(), get()) }
    single { CurrentUserStore() }
    single { DemoModeStore(get()) }
    single { AppStartup(get(), get(), get(), get()) }
    single { FavoriteRoutesStore(get()) }
    single { ThemeStore() }
}

val viewModelModule = module {
    viewModel { LoginViewModel(get(), get(), get()) }
    viewModel { MapViewModel(get(), get(), get(), get(), get()) }
    viewModel { SearchViewModel(get()) }
    viewModel { FiltersViewModel(get()) }
    viewModel { (placeId: String) -> PlaceDetailViewModel(placeId, get(), get(), get(), get()) }
    viewModel { (placeId: String) -> QuickCheckViewModel(placeId, get(), get(), get(), get(), get()) }
    viewModel { (placeId: String) -> PhotoUploadViewModel(placeId, get(), get(), get()) }
    viewModel { (placeId: String) -> RouteViewModel(get(), get(), get(), get(), placeId) }
    viewModel { FavoritesViewModel(get(), get(), get(), get()) }
    viewModel { ProfileViewModel(get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get(), get(), get(), get()) }
    viewModel { NotificationsViewModel(get()) }
    viewModel { SosRequestViewModel(get(), get(), get()) }
}

val appModules = listOf(networkModule, repositoryModule, useCaseModule, stateModule, viewModelModule)
