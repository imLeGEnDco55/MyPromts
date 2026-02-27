# Roadmap y Sugerencias — MyPromts

## Apps similares que vale la pena estudiar

### 🏆 La referencia más directa: **Text Blaze** (Chrome Extension / Web)
[textblaze.com](https://blaze.today)

El estándar de oro para "snippets de texto en cualquier campo". Lo que hacen bien:
- **Variables dinámicas dentro del snippet**: `{nombre}`, `{fecha}`, campos que se rellenan con un formulario antes de insertar
- **Comandos de teclado**: escribe `/revisatexto` y se expande automáticamente
- **Sincronización en la nube** entre dispositivos
- **Carpetas y sub-carpetas** sin límite de profundidad

**Diferencia con MyPromts**: Text Blaze vive en el navegador, tú vives en el sistema Android. Esa es tu ventaja competitiva.

---

### 🤖 **Espanso** (Desktop, open source)
[espanso.org](https://espanso.org)

Text expander multiplataforma. Interesante porque es open source y su modelo de datos (triggers + replacements) es una inspiración directa para prompts con variables.

---

### 📋 **Gboard — Snippets / Portapapeles inteligente**

El propio Gboard de Google tiene historial de portapapeles y snippets básicos. Lo interesante: está **integrado en el sistema**, sin fricción. La lección es que la velocidad de acceso importa más que las funciones.

---

### 🗃️ **PromptBase** (Marketplace)
[promptbase.com](https://promptbase.com)

No es un gestor local sino un marketplace. Útil para inspiración de **categorización y metadatos** de prompts (modelo de IA, precio, rating, casos de uso).

---

## Qué implementaría yo

### 🔴 Alta prioridad (resuelven fricciones reales)

#### 1. Variables en prompts `{placeholder}`
El feature más impactante. Un prompt como:
```
Actúa como experto en {área}. Revisa el siguiente texto con tono {formal/casual}: {texto}
```
Al insertar, aparece un mini-formulario que rellena los huecos antes de escribir. Implementación:

```kotlin
data class PromptVariable(val key: String, val defaultValue: String = "")

// Al tocar un prompt con variables:
fun parseVariables(content: String): List<PromptVariable> {
    val regex = Regex("\\{([^}]+)\\}")
    return regex.findAll(content).map { PromptVariable(it.groupValues[1]) }.toList()
}
```

#### 2. Búsqueda en el teclado IME
Ahora mismo el teclado no tiene buscador. Con una biblioteca de 100+ prompts, navegar por carpetas se vuelve tedioso. Un simple `OutlinedTextField` en el header del IME con `searchPrompts(query)` resuelve esto.

#### 3. Backup con imágenes incluidas
El backup actual guarda rutas locales que no sirven en otro dispositivo. Solución: exportar las imágenes como base64 dentro del JSON, o como ZIP con el JSON + carpeta de imágenes.

```kotlin
// En exportBackup():
"iconBase64" to Base64.encodeToString(File(s.iconIdentifier).readBytes(), Base64.DEFAULT)
```

---

### 🟡 Media prioridad (mejoran experiencia)

#### 4. ViewModel + Repositorio
Sacar el estado de los composables a `ViewModel` con `StateFlow` elimina:
- Pérdida de estado en rotación
- Llamadas duplicadas a `getDatabase(context)` en cada recomposición
- Lógica de negocio mezclada con UI

```kotlin
class PromptViewModel(private val repo: PromptRepository) : ViewModel() {
    val services = repo.getAllServices().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}
```

#### 5. Sincronización en la nube (Google Drive API o Supabase)
El backup manual en Downloads es frágil. Con una cuenta Google ya vinculada al dispositivo, un sync automático a Drive (usando la Drive API o el `BackupAgent` de Android) haría la app mucho más confiable.

#### 6. Tags cruzados entre servicios
Ahora un prompt vive exactamente en un Servicio → Categoría. Un sistema de tags permitiría marcar prompts como `#redacción`, `#código`, `#traducción` y filtrarlos sin importar el servicio.

#### 7. Historial de uso y analytics locales
`lastUsed` ya existe en `PromptEntity`. Con eso se puede construir:
- "Top 10 más usados esta semana"
- Ordenar prompts por frecuencia dentro del teclado
- Sugerir prompts según la app activa (detectar el `packageName` del campo activo)

```kotlin
// En onStartInputView():
val appPackage = currentInputEditorInfo?.packageName
// "com.openai.chatgpt" → mostrar primero prompts de ChatGPT
```

---

### 🟢 Baja prioridad (nice to have)

#### 8. Importar prompts desde URL o Markdown
Parsear un archivo `.md` con estructura de prompts y poblarlo automáticamente en la BD. Útil para migrar desde Notion, Obsidian, etc.

#### 9. Widget de inicio rápido
Un widget de Android que muestre los 4 prompts más usados. Un toque → copia al portapapeles.

#### 10. Modo "copia al portapapeles" vs "escribe en campo"
Algunas apps bloquean el acceso al `InputConnection`. Ofrecer la opción de copiar el prompt en lugar de insertarlo directamente.

#### 11. Shortcuts de expansión automática (como Espanso)
Detectar que el usuario escribió `/p1` en cualquier app y expandirlo al prompt asignado. Requiere accesibilidad o un sistema de reconocimiento más agresivo — más complejo, pero diferenciador.

#### 12. Compartir prompt individual como tarjeta de imagen
Generar una imagen bonita con el título y contenido del prompt para compartir en redes. La gente en Twitter comparte prompts constantemente.

---

## Deuda técnica a resolver

| Problema | Solución |
|---|---|
| `runBlocking` en `SavePromptActivity` | Extender `ComponentActivity` y usar `lifecycleScope` |
| `exportSchema = false` en Room | Activarlo y commitear los schemas para auditar migraciones |
| Sin tests unitarios de lógica de negocio | Extraer `BackupManager` y la lógica de parseo a módulo testeable |
| Imágenes acumuladas sin limpiar | Al eliminar un servicio, borrar también el archivo de imagen asociado |
| `MainActivity` con 700+ líneas | Extraer cada Dialog a su propio archivo composable |

---

## Versiones planificadas

### v2.1 — Calidad
- [ ] Variables en prompts `{placeholder}`
- [ ] Búsqueda en el teclado IME
- [ ] Backup con imágenes (base64 o ZIP)
- [ ] Limpieza de imágenes huérfanas al borrar servicio

### v2.2 — Arquitectura
- [ ] ViewModel + Repository pattern
- [ ] Tests unitarios para BackupManager
- [ ] Composables extraídos a archivos individuales
- [ ] Room exportSchema activado

### v3.0 — Plataforma
- [ ] Sincronización con Google Drive
- [ ] Tags cruzados
- [ ] Analytics de uso local
- [ ] Detección de app activa para sugerencias contextuales
