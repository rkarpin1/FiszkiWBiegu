// -------------------------------------------------------------------------------------------------
//   Copyright 2026 (c) Robert Karpiński
// -------------------------------------------------------------------------------------------------

package pl.rkarpinski.fiszkiwbiegu

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import pl.rkarpinski.fiszkiwbiegu.data.api.AuthEventBus
import pl.rkarpinski.fiszkiwbiegu.data.api.CollectionDto
import pl.rkarpinski.fiszkiwbiegu.data.api.FlashcardDto
import pl.rkarpinski.fiszkiwbiegu.data.repository.AuthRepository
import pl.rkarpinski.fiszkiwbiegu.screens.collections.CollectionFormScreen
import pl.rkarpinski.fiszkiwbiegu.screens.collections.CollectionsScreen
import pl.rkarpinski.fiszkiwbiegu.screens.collections.CollectionsViewModel
import pl.rkarpinski.fiszkiwbiegu.screens.flashcards.CardFormScreen
import pl.rkarpinski.fiszkiwbiegu.screens.flashcards.FlashcardsActions
import pl.rkarpinski.fiszkiwbiegu.screens.flashcards.FlashcardsScreen
import pl.rkarpinski.fiszkiwbiegu.screens.learning.LearningScreen
import pl.rkarpinski.fiszkiwbiegu.screens.login.LoginScreen
import pl.rkarpinski.fiszkiwbiegu.screens.profile.ProfileScreen
import pl.rkarpinski.fiszkiwbiegu.theme.FiszkiAppTheme
import pl.rkarpinski.fiszkiwbiegu.theme.FiszkiThemedScreen


private sealed interface Route {
    data object Login : Route
    data object Collections : Route
    data class Flashcards(val collection: CollectionDto) : Route
    data class Learning(val collection: CollectionDto) : Route
    data object Profile : Route
    data class CollectionForm(val collection: CollectionDto? = null) : Route
    data class CardForm(
        val collection: CollectionDto,
        val flashcard: FlashcardDto? = null,
    ) : Route
}

@Composable
fun App(
    onGoogleSignIn: suspend () -> Result<String>,
    initialCollectionJson: String? = null,
) {
    val authRepository: AuthRepository = koinInject()
    val authEventBus: AuthEventBus = koinInject()
    val collectionsVm: CollectionsViewModel = koinViewModel()
    val scope = rememberCoroutineScope()

    val initial: Route = remember {
        val initialCollection = initialCollectionJson?.let {
            try {
                Json.decodeFromString<CollectionDto>(it)
            } catch (e: Exception) {
                null
            }
        }
        if (initialCollection != null) {
            Route.Learning(initialCollection)
        } else if (authRepository.isLoggedIn()) {
            Route.Collections
        } else {
            Route.Login
        }
    }
    val backStack = remember {
        val list = mutableStateListOf<Route>()
        if (authRepository.isLoggedIn()) {
            list.add(Route.Collections)
            if (initial is Route.Learning) {
                list.add(initial)
            }
        } else {
            list.add(initial)
        }
        list
    }

    LaunchedEffect(initialCollectionJson) {
        val collection = initialCollectionJson?.let {
            try {
                Json.decodeFromString<CollectionDto>(it)
            } catch (e: Exception) {
                null
            }
        }
        if (collection != null) {
            val targetRoute = Route.Learning(collection)
            if (backStack.lastOrNull() != targetRoute) {
                if (authRepository.isLoggedIn()) {
                    backStack.clear()
                    backStack.add(Route.Collections)
                    backStack.add(targetRoute)
                } else {
                    backStack.clear()
                    backStack.add(Route.Login)
                }
            }
        }
    }

    var loginError by remember { mutableStateOf<String?>(null) }
    var isLoggingIn by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        authEventBus.unauthorizedEvents.collect {
            authRepository.logout()
            backStack.clear()
            backStack.add(Route.Login)
        }
    }

    FiszkiAppTheme(override = null) {
        FiszkiThemedScreen(naturalDark = true) {
            val currentRoute = backStack.lastOrNull()
            val showTabBar = currentRoute is Route.Collections || currentRoute is Route.Profile

            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    if (showTabBar) {
                        AppBottomBar(
                            current = currentRoute,
                            onCollections = {
                                if (currentRoute is Route.Profile) backStack.removeLastOrNull()
                            },
                            onProfile = {
                                if (currentRoute !is Route.Profile) backStack.add(Route.Profile)
                            },
                        )
                    }
                },
            ) { paddingValues ->
                NavDisplay(
                    backStack = backStack,
                    modifier = Modifier.padding(paddingValues),
                    entryProvider = entryProvider {
                        entry<Route.Login> {
                            LoginScreen(
                                isLoading = isLoggingIn,
                                error = loginError,
                                onSignInClick = {
                                    isLoggingIn = true
                                    loginError = null
                                    scope.launch {
                                        onGoogleSignIn().fold(
                                            onSuccess = { idToken ->
                                                authRepository.loginWithGoogle(idToken).fold(
                                                    onSuccess = {
                                                        backStack.clear()
                                                        backStack.add(Route.Collections)
                                                    },
                                                    onFailure = { e ->
                                                        loginError = e.message ?: "Błąd logowania"
                                                    },
                                                )
                                            },
                                            onFailure = { e ->
                                                loginError = e.message ?: "Błąd Google Sign-In"
                                            },
                                        )
                                        isLoggingIn = false
                                    }
                                },
                            )
                        }
                        entry<Route.Collections> {
                            CollectionsScreen(
                                viewModel = collectionsVm,
                                onCollectionClick = { backStack.add(Route.Flashcards(it)) },
                                onAddClick = { backStack.add(Route.CollectionForm()) },
                            )
                        }
                        entry<Route.Flashcards> { route ->
                            val collectionsState by collectionsVm.uiState.collectAsState()
                            val collection = collectionsState.collections.find { it.id == route.collection.id }
                                ?: route.collection
                            FlashcardsScreen(
                                collection = collection,
                                actions = object : FlashcardsActions {
                                    override fun onBack() { backStack.removeLastOrNull() }
                                    override fun onStartLearning() { backStack.add(Route.Learning(collection)) }
                                    override fun onAddCard() { backStack.add(Route.CardForm(collection)) }
                                    override fun onEditCard(flashcard: FlashcardDto) { backStack.add(Route.CardForm(collection, flashcard)) }
                                    override fun onEditCollection() { backStack.add(Route.CollectionForm(collection)) }
                                    override fun onDeleteCollection() {
                                        collectionsVm.deleteCollection(collection.id)
                                        backStack.removeLastOrNull()
                                    }
                                },
                            )
                        }
                        entry<Route.CollectionForm> { route ->
                            CollectionFormScreen(
                                collection = route.collection,
                                viewModel = collectionsVm,
                                onBack = { backStack.removeLastOrNull() },
                            )
                        }
                        entry<Route.CardForm> { route ->
                            CardFormScreen(
                                collection = route.collection,
                                flashcard = route.flashcard,
                                onBack = { backStack.removeLastOrNull() },
                            )
                        }
                        entry<Route.Learning> { route ->
                            LearningScreen(
                                collection = route.collection,
                                onBack = {
                                    backStack.clear()
                                    backStack.add(Route.Collections)
                                },
                            )
                        }
                        entry<Route.Profile> {
                            ProfileScreen(
                                onLogout = {
                                    authRepository.logout()
                                    backStack.clear()
                                    backStack.add(Route.Login)
                                },
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun AppBottomBar(
    current: Route?,
    onCollections: () -> Unit,
    onProfile: () -> Unit,
) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        NavigationBarItem(
            selected = current is Route.Collections,
            onClick = onCollections,
            icon = { Icon(Icons.AutoMirrored.Filled.LibraryBooks, contentDescription = null) },
            label = { Text("Kolekcje") },
        )
        NavigationBarItem(
            selected = current is Route.Profile,
            onClick = onProfile,
            icon = { Icon(Icons.Default.AccountCircle, contentDescription = null) },
            label = { Text("Konto") },
        )
    }
}
