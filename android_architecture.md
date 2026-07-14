# Recommendations for Android Architecture

This document provides best practices and recommendations for Android architecture to improve app quality, robustness, scalability, maintainability, and testability.  These are recommendations, not strict requirements, and should be adapted as needed.  Recommendations are categorized by priority:

* **Strongly recommended:** Implement unless there's a fundamental clash with your approach.
* **Recommended:** Likely to improve your app.
* **Optional:** Can improve your app in certain situations.

Familiarity with general Android architecture guidance is assumed.

## Layered Architecture

The recommended layered architecture promotes separation of concerns, driving UI from data models, adhering to the single source of truth principle, and following unidirectional data flow.

* **Strongly recommended:** Use a clearly defined data layer. This layer exposes application data and contains most business logic. Create repositories even with a single data source. In small apps, place data layer types in a `data` package or module.
* **Strongly recommended:** Use a clearly defined UI layer. This layer displays data and handles user interaction. In small apps, place UI layer types in a `ui` package or module.  More UI layer best practices are detailed later.
* **Strongly recommended:** The data layer should expose data via a repository. UI components (composables, activities, ViewModels) should not interact directly with data sources (databases, DataStore, network, etc.).
* **Strongly recommended:** Use coroutines and flows for inter-layer communication. More coroutines best practices are available elsewhere.
* **Recommended (in big apps):** Use a domain layer and use cases for reusable business logic across multiple ViewModels or to simplify ViewModel logic.

## UI Layer

The UI layer displays data and handles user interaction.

* **Strongly recommended:** Follow Unidirectional Data Flow (UDF). ViewModels expose UI state using the observer pattern and receive actions via method calls.
* **Strongly recommended:** Use AAC ViewModels for business logic and fetching data for UI state. More ViewModel best practices are detailed later.  Benefits of ViewModels are discussed elsewhere.
* **Strongly recommended:** Use lifecycle-aware UI state collection. Use `repeatOnLifecycle` in the View system and `collectAsStateWithLifecycle` in Jetpack Compose. In your composable, you can use a when expression to handle the state as follows:
```kotlin
val uiState = viewModel.uiState.collectAsStateWithLifecycle()

when (uiState) {
    ScreenState.Initial -> {
      // Show initial state
    }

    ScreenState.Generating -> {
      // Show generating state
    }

    is ScreenState.Success -> {
      // Show success state
    }

    is ScreenState.Error -> {
      // Show error state
    }
}
```
* **Strongly recommended:** Do not send events from the ViewModel to the UI. Process events in the ViewModel and update the state accordingly. More on UI events is available elsewhere.
* **Strongly recommended:** Every composable function (except top level screen composable) should take a `Modifier` as a parameter with a default value. It should be positionned as the first optional parameter.
* **Recommended:** Use a single-activity application.  Use Navigation Fragments or Navigation Compose for navigation and deep linking.
* **Recommended:** Use Jetpack Compose for new apps on phones, tablets, foldables, and Wear OS.

## ViewModel

ViewModels provide UI state and data layer access.

* **Strongly recommended:** ViewModels should be agnostic of the Android lifecycle. Avoid references to lifecycle-related types, Activities, Fragments, Context, or Resources.  Evaluate if dependencies on Context belong in the ViewModel.
* **Strongly recommended:** ViewModel functions should never take an activity as a parameter to avoid memory leaks.  
* **Strongly recommended:** Use coroutines and flows. ViewModels interact with data/domain layers using Kotlin flows for receiving data and `suspend` functions with `viewModelScope` for actions.
* **Strongly recommended:** Use ViewModels at the screen level. Avoid using them in reusable UI components. Use them in screen-level composables, Activities/Fragments, and Navigation destinations/graphs.
* **Strongly recommended:** Use plain state holder classes in reusable UI components. This allows external control over the state.
* **Strongly recommended:** ViewModel should only expose one UI state defined as a sealed class. e.g:
```kotlin
sealed class ScreenState {
   data object Initial : ScreenState()
   data object Generating : ScreenState()  // Use for generating content
   data class Success(val data: String) : ScreenState() // Use to display data
   data class Error(val message: String) : ScreenState() // Use for error state
}

```
* **Recommended:** Do not use `AndroidViewModel`. Use the `ViewModel` class. Avoid using the `Application` class in ViewModels; move the dependency to the UI or data layer.
* **Recommended:** Don't use `LiveData`, use state flow instead.
* **Recommended:** Expose a UI state. Use a single `uiState` property (a `StateFlow`) for data exposure. Multiple properties can be used for unrelated data. Use `stateIn` with `WhileSubscribed(5000)` for data streams.  For simpler cases, use a `MutableStateFlow` exposed as an immutable `StateFlow`.  Consider using a data class or sealed class for the `UiState`.
* **Recommeded:** Don’t pass `Context` to your `ViewModel`. To avoid memory leaks only UI (Composables) should have a reference to `context`.
* **Recommeded:** Don’t use `fetchData()` in a ViewModel  `init {}` block. If you do end up in a case where you need to do something on initial load, you use `stateIn()` instead. 


## Lifecycle

Best practices for working with the Android lifecycle:

* **Strongly recommended:** Do not override lifecycle methods (e.g., `onResume`) in Activities or Fragments. Use `LifecycleObserver` instead. For lifecycle-dependent work, use `repeatOnLifecycle`.

## Handle Dependencies

Best practices for dependency management:

* **Strongly recommended:** Use dependency injection, primarily constructor injection.
* **Strongly recommended:** Scope to a dependency container when sharing mutable data or for expensive, widely used types.
* **Recommended:** Use Hilt for complex projects or manual dependency injection for simpler apps. Hilt is beneficial for multiple ViewModels, WorkManager usage, and advanced Navigation.

## Testing

Best practices for testing:

* **Strongly recommended:** Know what to test.  Test ViewModels (including Flows), data layer entities (repositories and data sources), and UI navigation for regression testing.
* **Strongly recommended:** Prefer fakes over mocks. See the Android documentation on test doubles.
* **Strongly recommended:** Test StateFlows. Assert on the `value` property when possible. Create a `collectJob` when using `WhileSubscribed`.

## Models

Best practices for models:

* **Recommended:** Create a model per layer in complex apps. Consider creating separate models for different layers (e.g., remote data source, repositories, ViewModels) to simplify data structures and reduce dependencies.

## Naming Conventions

Optional naming conventions:

* Methods: Verb phrases (e.g., `makePayment()`).
* Properties: Noun phrases (e.g., `inProgressTopicSelection`).
* Streams: `get{Model}Stream()` (e.g., `getAuthorStream(): Flow<Author>`, `getAuthorsStream(): Flow<List<Author>>`).
* Interface Implementations: Meaningful names. Prefix with `Default` if no better name is found (e.g., `OfflineFirstNewsRepository`, `InMemoryNewsRepository`, `DefaultNewsRepository`). Prefix fake implementations with `Fake` (e.g., `FakeAuthorsRepository`).


