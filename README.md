# MyPromts 🧠⌨️

> **Una biblioteca de prompts de IA integrada directamente en tu teclado Android.**

Guarda, organiza y usa tus prompts favoritos para ChatGPT, Claude, MidJourney y cualquier otro servicio de IA, sin salir de ninguna app. Escríbelos en cualquier campo de texto con un solo toque.

---

## ¿Qué hace?

MyPromts tiene dos modos de vida:

**Como app principal** → Gestiona tu biblioteca. Crea servicios (ej: "Claude"), categorías (ej: "Redacción"), y dentro de cada una tus prompts. Búsqueda global, favoritos, backup, todo en un solo lugar.

**Como teclado** → Cuando estás en cualquier otra app (WhatsApp, navegador, ChatGPT...) activas el teclado de MyPromts, navegas por tu biblioteca y tocas el prompt. Se escribe solo en el campo activo.

---

## Pantallas principales

```
SERVICIOS → CATEGORÍAS → PROMPTS
   └── Búsqueda global
   └── Borradores (textos capturados con "Compartir")
   └── Teclado IME con navegación propia
```

### Flujo del Teclado (IME)

```
[Recientes]
[Servicio 1] [Servicio 2] [Servicio 3]
      ↓
[Categoría A] [Categoría B]
      ↓
[Prompt 1] → toca → se inserta en el campo activo
[Prompt 2]
```

---

## Instalación y setup

### Requisitos
- Android 13+ (minSdk 33)
- Arquitectura arm64-v8a (optimizado para MediaTek Helio G99-Ultra / Redmi Note 14 4G)
- Compilar con JDK 21, Kotlin 2.0.21

### Build
```bash
# Debug
./gradlew assembleDebug

# Release (requiere keystore.jks en raíz del proyecto)
./gradlew assembleRelease
```

### Activar el teclado
1. Abre la app → toca **"Activar Teclado"**
2. En el selector del sistema, activa **MyPromts**
3. Cuando quieras usarlo, cambia de teclado desde la notificación persistente o el selector

---

## Permisos necesarios

| Permiso | Para qué |
|---|---|
| `SYSTEM_ALERT_WINDOW` | Selector de teclado sobre otras apps |
| `FOREGROUND_SERVICE` | Notificación persistente de cambio de teclado |
| `POST_NOTIFICATIONS` | Mostrar la notificación (Android 13+) |
| `RECEIVE_BOOT_COMPLETED` | Restaurar la notificación al reiniciar |
| `READ/WRITE_EXTERNAL_STORAGE` | Backups en la carpeta Downloads |

---

## Características

- ✅ Jerarquía Servicio → Categoría → Prompt
- ✅ Favoritos con acceso rápido desde el teclado
- ✅ Prompts recientes (últimos 10 usados)
- ✅ Búsqueda global por título y contenido
- ✅ Captura de texto desde cualquier app ("Compartir → MyPromts")
- ✅ Backup/Restore manual en JSON (carpeta Downloads)
- ✅ Auto-backup silencioso en cada cambio
- ✅ Notificación persistente para cambio rápido de teclado
- ✅ Duplicar, editar, eliminar prompts
- ✅ Reordenar servicios
- ✅ Iconos personalizados por servicio (foto del galería)

---

## Stack tecnológico

| Capa | Tecnología |
|---|---|
| UI App | Jetpack Compose + Material 3 |
| UI Teclado | Jetpack Compose dentro de `InputMethodService` |
| Base de datos | Room (SQLite) |
| Imágenes | Coil |
| Arquitectura | Single-Activity, estado con `remember`/`collectAsState` |
| Persistencia | Room + SharedPreferences + JSON backup |
| Inyección | Sin DI (instanciación directa, scope por composable) |

---

## Estructura de archivos

```
app/src/main/java/com/imlegendco/mypromts/
├── MainActivity.kt              # Pantalla principal + todos los diálogos
├── MyInputMethodService.kt      # Teclado IME con Compose
├── SavePromptActivity.kt        # Receptor de "Compartir texto"
├── ActivateKeyboardActivity.kt  # Abre el selector del sistema
├── KeyboardSwitcherService.kt   # Servicio de notificación persistente
├── BootReceiver.kt              # Reactiva el servicio al reiniciar
├── database/
│   ├── AppDatabase.kt           # Room database, migraciones
│   ├── ServiceEntity.kt / ServiceDao.kt
│   ├── CategoryEntity.kt / CategoryDao.kt
│   ├── PromptEntity.kt / PromptDao.kt
│   └── DraftPromptEntity.kt / DraftPromptDao.kt
├── utils/
│   ├── BackupManager.kt         # Export/Import JSON
│   └── ImageStorageManager.kt   # Guarda imágenes en almacenamiento interno
└── ui/theme/
    ├── Color.kt                 # Paleta cyberpunk/magenta
    ├── Theme.kt                 # MaterialTheme oscuro
    └── Type.kt                  # Tipografía
```

---

## Modelo de datos

```
ServiceEntity
  id, name, iconIdentifier (ruta local), orderIndex
    │
    └── CategoryEntity
          id, name, serviceId (FK → Service, CASCADE)
              │
              └── PromptEntity
                    id, title, content, categoryId (FK → Category, CASCADE)
                    isFavorite, lastUsed

DraftPromptEntity
  id, content, timestamp
  (independiente, sin FK)
```

---

## Backup

El backup es un archivo JSON con esta estructura:

```json
{
  "version": 1,
  "exportDate": 1700000000000,
  "services": [{ "id": 1, "name": "Claude", "iconIdentifier": "/path/icon.jpg" }],
  "categories": [{ "id": 1, "name": "Redacción", "serviceId": 1 }],
  "prompts": [{ "id": 1, "title": "Revisar texto", "content": "...", "categoryId": 1, "isFavorite": true, "lastUsed": 0 }]
}
```

Al importar, los IDs se reasignan automáticamente para evitar colisiones con datos existentes.

---

## Créditos

- Desarrollado con: **Gemini**
- Vibecodeado por: **@ElWaiELe**
- Hecho con ✨ en **Antigravity**
- Powered by **Google**

Versión: **2.0.0**
