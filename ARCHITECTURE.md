# Arquitectura Técnica — MyPromts

## Visión general

MyPromts combina dos tipos de componentes Android que normalmente viven separados: una app de gestión y un `InputMethodService` (teclado). Ambos comparten la misma base de datos Room.

```
┌──────────────────────────────────────────────┐
│              Proceso de la App                │
│                                              │
│  MainActivity          MyInputMethodService  │
│  (Compose UI)          (Compose IME)         │
│       │                       │              │
│       └───────────┬───────────┘              │
│                   │                          │
│             AppDatabase (Room)               │
│        Services / Categories / Prompts       │
│              DraftPrompts                    │
└──────────────────────────────────────────────┘
```

---

## Componentes principales

### `MainActivity`

Actividad única que contiene toda la UI de gestión como composables internos.

**Estado de navegación:**
```kotlin
enum class AppNavigation { SERVICES, CATEGORIES, PROMPTS, DRAFTS }
```

La navegación es manual con `var currentNav by remember { mutableStateOf(...) }` en lugar de NavController, lo que simplifica la arquitectura pero acopla la lógica al composable raíz.

**Patrón de datos:**
```kotlin
// Cada Flow se suscribe solo cuando su nivel es visible
val allServices by database.serviceDao().getAllServices().collectAsState(initial = emptyList())

val categoriesForService by remember(selectedService) {
    selectedService?.let { database.categoryDao().getCategoriesForService(it.id) } ?: flowOf(emptyList())
}.collectAsState(initial = emptyList())
```

El uso de `remember(key)` garantiza que el Flow cambia (y se cancela el anterior) cuando cambia el servicio seleccionado.

---

### `MyInputMethodService`

El teclado es un `InputMethodService` que implementa tres interfaces para poder hospedar Compose:

```kotlin
class MyInputMethodService : InputMethodService(),
    LifecycleOwner,           // para collectAsState en composables
    ViewModelStoreOwner,      // requerido por ViewModelStoreOwner tree
    SavedStateRegistryOwner   // requerido por el árbol de Compose
```

**Por qué estas interfaces:**
Compose necesita un `LifecycleOwner` válido en el árbol de vistas para que funcionen las APIs de ciclo de vida (como `collectAsState`). Un `InputMethodService` no extiende `AppCompatActivity`, así que hay que implementarlo manualmente.

```kotlin
override fun onCreateInputView(): View {
    val composeView = ComposeView(this).apply {
        setViewTreeLifecycleOwner(this@MyInputMethodService)
        setViewTreeSavedStateRegistryOwner(this@MyInputMethodService)
        setViewTreeViewModelStoreOwner(this@MyInputMethodService)
        setContent { ... }
    }
    // También en decorView por si Compose busca ahí
    window?.window?.decorView?.let { decorView ->
        decorView.setViewTreeLifecycleOwner(this)
        ...
    }
    return composeView
}
```

**Insertar texto en el campo activo:**
```kotlin
val ic = currentInputConnection
ic?.commitText(prompt.content, 1) // cursor al final
```

---

### `SavePromptActivity`

Receptor del intent `ACTION_PROCESS_TEXT`. Cuando el usuario selecciona texto en cualquier app y elige "MyPromts" en el menú Compartir, este Activity transparente lo guarda como borrador.

```kotlin
// En AndroidManifest.xml:
<intent-filter>
    <action android:name="android.intent.action.PROCESS_TEXT" />
    <data android:mimeType="text/plain" />
</intent-filter>
```

Extiende `Activity` (no `ComponentActivity`) para ser lo más liviano posible. Usa `runBlocking` para la operación de BD porque no tiene `lifecycleScope`.

---

### `KeyboardSwitcherService`

Foreground Service que mantiene una notificación persistente. Al tocar la notificación, lanza `ActivateKeyboardActivity` que llama a `InputMethodManager.showInputMethodPicker()`.

```
Notificación → PendingIntent → ActivateKeyboardActivity → showInputMethodPicker()
```

`BootReceiver` lo reactiva tras reinicio si el usuario lo tenía activado (preferencia en SharedPreferences).

---

## Base de datos

### Room con versión 6

```kotlin
@Database(
    entities = [ServiceEntity::class, CategoryEntity::class, 
                PromptEntity::class, DraftPromptEntity::class],
    version = 6,
    exportSchema = false
)
```

**Migraciones disponibles:**
- v5 → v6: `ALTER TABLE services ADD COLUMN orderIndex INTEGER NOT NULL DEFAULT 0`

**Cascadas:**
```
Service (DELETE) → CASCADE → Category (DELETE) → CASCADE → Prompt
```
Eliminar un servicio borra todo su árbol.

**Singleton pattern:**
```kotlin
companion object {
    @Volatile private var INSTANCE: AppDatabase? = null
    fun getDatabase(context: Context): AppDatabase { ... }
}
```

---

## Gestión de imágenes

Los iconos de servicios se guardan en el almacenamiento interno de la app (`context.filesDir`), no en el URI original del picker (que puede revocarse). La ruta absoluta se guarda en `ServiceEntity.iconIdentifier`.

```kotlin
// ImageStorageManager.kt
val file = File(context.filesDir, "service_${timestamp}_${uuid}.jpg")
FileOutputStream(file).use { outputStream ->
    inputStream.copyTo(outputStream)
}
return file.absolutePath
```

**Coil** carga la imagen directamente desde `File(path)`.

---

## Backup — Mapeo de IDs

El problema del import: los IDs en el JSON son los de la DB origen. En la DB destino pueden existir registros con esos mismos IDs.

**Solución:** Re-asignar IDs al importar con un mapa viejo→nuevo:

```kotlin
val serviceIdMap = mutableMapOf<Int, Int>()

// Al insertar cada servicio:
val newId = database.serviceDao().insertServiceReturnId(service.copy(id = 0))
serviceIdMap[oldId] = newId.toInt()

// Al insertar categorías:
val newServiceId = serviceIdMap[oldServiceId] ?: continue // skip si no existe
```

---

## Tema visual

Paleta cyberpunk con magenta como primario sobre fondo casi negro:

```kotlin
private val MyPromtsDarkColorScheme = darkColorScheme(
    primary    = Color(0xFFFF00FF),  // Magenta
    secondary  = Color(0xFF00FFFF),  // Cyan
    tertiary   = Color(0xFFFFD700),  // Gold
    background = Color(0xFF0D0D0D),
    surface    = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFF252525)
)
```

El teclado IME usa `darkColorScheme()` de Material3 directamente (sin el tema personalizado) para garantizar contraste correcto en el contexto de IME.

---

## Consideraciones de rendimiento

- `ndk { abiFilters += listOf("arm64-v8a") }` — APK solo para la arquitectura del dispositivo objetivo
- `localeFilters += listOf("es", "en")` — Elimina recursos de otros idiomas
- `isMinifyEnabled = true` + `isShrinkResources = true` en release
- ProGuard con 5 pases de optimización y repackaging de clases

---

## Limitaciones conocidas

| Limitación | Detalle |
|---|---|
| Sin ViewModel | Estado vive en composables; se pierde en rotación de pantalla |
| Sin DI | `AppDatabase.getDatabase(context)` llamado en cada composable |
| `runBlocking` en `SavePromptActivity` | Bloquea el hilo del UI brevemente al guardar borradores |
| Imágenes no incluidas en backup | El backup JSON guarda rutas locales, que no son válidas en otro dispositivo |
| Export schema = false | No hay historial de esquemas para auditoría de migraciones |
