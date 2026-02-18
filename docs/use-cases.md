# Custom Annotation Use Cases

This document describes the custom annotations needed for the android-register, what each one generates, and example usages. These use cases are
implementation-agnostic -- they can be fulfilled by KSP processors, a compiler plugin
feature, or any other code generation mechanism.

---

## @ContributesFeatureFlag

Contributes a `FeatureFlag` object into `Set<FeatureFlag>` via `@IntoSet`. Always scoped
to `AppScope` since all feature flags live in the app scope.

The `description`, `removeBy`, and `fakeModeValues` parameters are metadata only -- used by
linters and tooling, not by the generated binding.

### Annotation definition

```kotlin
annotation class ContributesFeatureFlag(
  val description: String,
  val removeBy: Date,
  val fakeModeValues: Array<FlagValue> = [],
)
```

### Target

`object` classes implementing `FeatureFlag`.

### Usage

```kotlin
@ContributesFeatureFlag(
  description = "Enables new checkout flow",
  removeBy = Date(Month.June, 1, 2026),
)
object NewCheckoutFlag : FeatureFlag { ... }
```

### Generated output (pseudo)

```kotlin
@ContributesTo(AppScope::class)
@BindingContainer
object NewCheckoutFlagBindingContainer {
  @Provides @IntoSet
  fun contributeFlag(): FeatureFlag = NewCheckoutFlag
}
```

---

## @ContributesDynamicConfigurationFlag

Identical to `@ContributesFeatureFlag` but for permanent flags that should not be removed.
Separate annotation for lint/policy enforcement.

### Annotation definition

```kotlin
annotation class ContributesDynamicConfigurationFlag(
  val description: String,
  val fakeModeValues: Array<FlagValue> = [],
)
```

### Target

`object` classes implementing `FeatureFlag`.

### Usage

```kotlin
@ContributesDynamicConfigurationFlag(
  description = "Controls max retry count for sync operations",
)
object SyncRetryConfig : FeatureFlag { ... }
```

### Generated output (pseudo)

```kotlin
@ContributesTo(AppScope::class)
@BindingContainer
object SyncRetryConfigBindingContainer {
  @Provides @IntoSet
  fun contributeFlag(): FeatureFlag = SyncRetryConfig
}
```

---

## @ContributesRobot

Generates a contributed interface with an abstract accessor method that exposes the
robot class on the dependency graph. The target must extend `ScreenRobot` or
`ComposeScreenRobot`.

### Annotation definition

```kotlin
annotation class ContributesRobot(
  val scope: KClass<*>,
)
```

### Target

`@Inject` classes extending `ScreenRobot` or `ComposeScreenRobot`.

### Usage

```kotlin
@ContributesRobot(AppScope::class)
@Inject
class LoginScreenRobot : ComposeScreenRobot<LoginScreenRobot>() {
  fun tapSignIn() { clickView(R.id.sign_in) }
  fun seeWelcomeMessage() { seeView(R.id.welcome) }
}
```

### Generated output (pseudo)

```kotlin
@ContributesTo(AppScope::class)
interface LoginScreenRobotComponent {
  fun getLoginScreenRobot(): LoginScreenRobot
}
```

This exposes the robot as an accessor on the merged graph, making it injectable at
the test site. Each robot gets its own contributed interface with a unique accessor
method name.

---

## @ContributesService

Contributes a Retrofit service binding to a scope. Handles two distinct paths: real
services (interface declarations) and fake services (classes that replace a real service
in debug builds).

The target must have exactly one `@Qualifier` annotation (e.g., `@RetrofitAuthenticated`,
`@RetrofitUnauthenticated`) which determines which `ServiceCreator` is injected.

### Annotation definition

```kotlin
annotation class ContributesService(
  val scope: KClass<*>,
  val replaces: Array<KClass<*>> = [],
)
```

### Target

- **Real services:** Retrofit service interfaces with a qualifier annotation.
- **Fake services:** `@Inject` classes in debug source sets that implement a real service
  interface, using `replaces` to specify which service they replace.

### Usage — Real service

```kotlin
@ContributesService(AppScope::class)
@RetrofitAuthenticated
interface RemoteDeviceApiService {
  @POST("/v2/devices")
  fun updateDevice(@Body request: UpdateDeviceRequest): Response<UpdateDeviceResponse>
}
```

### Generated output — Real service (pseudo)

In release builds:

```kotlin
@Module
@ContributesTo(AppScope::class)
object RemoteDeviceApiServiceModule {
  @Provides @SingleIn(AppScope::class)
  fun provideRemoteDeviceApiService(
    @RetrofitAuthenticated serviceCreator: ServiceCreator,
  ): RemoteDeviceApiService {
    return serviceCreator.create(RemoteDeviceApiService::class.java)
  }
}
```

In debug builds, an additional `@FakeMode` safety check is generated to catch cases
where fake mode is enabled but no fake service was provided:

```kotlin
@Module
@ContributesTo(AppScope::class)
object RemoteDeviceApiServiceModule {
  @Provides @SingleIn(AppScope::class)
  fun provideRemoteDeviceApiService(
    @RetrofitAuthenticated serviceCreator: ServiceCreator,
    @FakeMode isFakeMode: Boolean,
  ): RemoteDeviceApiService {
    check(!isFakeMode) { "No fake service provided for RemoteDeviceApiService." }
    return serviceCreator.create(RemoteDeviceApiService::class.java)
  }
}
```

### Usage — Fake service

```kotlin
// In src/debug
@SingleIn(AppScope::class)
@ContributesService(AppScope::class, replaces = [RemoteDeviceApiService::class])
@Inject
class FakeRemoteDeviceApiService(
  factory: MockServiceHelper.Factory,
) : RemoteDeviceApiService {
  private val mockHelper = factory.create<RemoteDeviceApiService>()
  override fun updateDevice(request: UpdateDeviceRequest) =
    mockHelper.mockResponse { UpdateDeviceResponse() }.updateDevice(request)
  // ...
}
```

### Generated output — Fake service (pseudo)

The generated module replaces the real service's module. It re-creates the real service
binding under a `@RealService` qualifier, then adds a switcher that picks real or fake
based on the `@FakeMode` boolean:

```kotlin
@Module
@ContributesTo(
  scope = AppScope::class,
  replaces = [RemoteDeviceApiServiceModule::class],
)
object FakeRemoteDeviceApiServiceModule {
  // Real service still available under @RealService qualifier
  @Provides @SingleIn(AppScope::class) @RealService
  fun provideRemoteDeviceApiService(
    @RetrofitAuthenticated serviceCreator: ServiceCreator,
  ): RemoteDeviceApiService {
    return serviceCreator.create(RemoteDeviceApiService::class.java)
  }

  // Switcher: returns fake or real based on runtime @FakeMode flag
  @Provides
  fun provideFakeOrRealRemoteDeviceApiService(
    @RealService realService: Provider<RemoteDeviceApiService>,
    fakeService: Provider<FakeRemoteDeviceApiService>,
    @FakeMode isFakeMode: Boolean,
  ): RemoteDeviceApiService {
    return if (isFakeMode) fakeService.get() else realService.get()
  }
}
```

The qualifier (e.g., `@RetrofitAuthenticated`) is read from the replaced service
interface's annotations. The fake class must extend all replaced service types. Both
real and fake must use the same scope.

---

## @ContributesMultibindingScoped

Contributes an `@Inject` class implementing `Scoped` into a `Set<Scoped>` qualified with
`@ForScope`. The scope value is used for both the contribution target and the `@ForScope`
qualifier, since they are always the same.

### Annotation definition

```kotlin
annotation class ContributesMultibindingScoped(
  val scope: KClass<*>,
)
```

### Target

`@Inject` classes implementing `Scoped`.

### Usage

```kotlin
@ContributesMultibindingScoped(AppScope::class)
@Inject
class AppLifecycleLogger : Scoped {
  override fun onEnterScope(scope: MortarScope) { log("entered") }
  override fun onExitScope() { log("exited") }
}
```

### Generated output (pseudo)

```kotlin
@ContributesTo(AppScope::class)
@BindingContainer
object AppLifecycleLoggerBindingContainer {
  @Provides @IntoSet @ForScope(AppScope::class)
  fun contributeScoped(target: AppLifecycleLogger): Scoped = target
}
```

Note: the scope from the annotation (`AppScope`) is used in two places -- as the
`@ContributesTo` scope and as the `@ForScope` qualifier value.
