package com.imlegendco.mypromts

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import android.content.Context
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.imlegendco.mypromts.database.AppDatabase
import com.imlegendco.mypromts.database.CategoryEntity
import com.imlegendco.mypromts.database.PromptEntity
import com.imlegendco.mypromts.database.ServiceEntity
import com.imlegendco.mypromts.ui.theme.MyPromtsTheme
import com.imlegendco.mypromts.utils.BackupManager
import com.imlegendco.mypromts.utils.ImageStorageManager
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.io.File

// Enum para navegación tipo carpetas
enum class AppNavigation { SERVICES, CATEGORIES, PROMPTS, DRAFTS }

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    // Estado para Notificación Persistente
    private var isNotificationEnabled by mutableStateOf(false)

    // Launcher para solicitar permiso de notificación
    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            isNotificationEnabled = true
            saveNotificationPreference(true)
            KeyboardSwitcherService.start(this)
        } else {
            isNotificationEnabled = false
            saveNotificationPreference(false)
            Toast.makeText(this, "Permiso de notificación denegado", Toast.LENGTH_SHORT).show()
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isNotificationEnabled = getNotificationPreference()
        if (isNotificationEnabled) {
            KeyboardSwitcherService.start(this)
        }

        enableEdgeToEdge()
        
        // Handle initial intent setup
        if (intent?.action == "com.imlegendco.mypromts.ACTION_ACTIVATE_KEYBOARD") {
            handleKeyboardActivationShortcut()
        }

        setContent {
            MyPromtsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ManagerScreen()
                }
            }
        }
    }

    @Composable
    fun ManagerScreen() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val database = remember { AppDatabase.getDatabase(context) }

        // Estado de navegación
        var currentNav by remember { mutableStateOf(AppNavigation.SERVICES) }
        var selectedService by remember { mutableStateOf<ServiceEntity?>(null) }
        var selectedCategory by remember { mutableStateOf<CategoryEntity?>(null) }
        
        // Estado de UI
        var searchQuery by remember { mutableStateOf("") }
        var showSettingsPanel by remember { mutableStateOf(false) }
        var showAddDialog by remember { mutableStateOf(false) }
        var showEditDialog by remember { mutableStateOf(false) }
        var promptToEdit by remember { mutableStateOf<PromptEntity?>(null) }
        var draftToSave by remember { mutableStateOf<com.imlegendco.mypromts.database.DraftPromptEntity?>(null) }
        var showQuickPromptDialog by remember { mutableStateOf(false) }
        var showEditServiceDialog by remember { mutableStateOf<ServiceEntity?>(null) }

        // BackHandler para navegación
        BackHandler(enabled = currentNav != AppNavigation.SERVICES || showSettingsPanel || showAddDialog || showEditDialog || draftToSave != null || showQuickPromptDialog || showEditServiceDialog != null) {
            when {
                showEditServiceDialog != null -> showEditServiceDialog = null
                showQuickPromptDialog -> showQuickPromptDialog = false
                draftToSave != null -> draftToSave = null
                showEditDialog -> showEditDialog = false
                showAddDialog -> showAddDialog = false
                showSettingsPanel -> showSettingsPanel = false
                currentNav == AppNavigation.DRAFTS -> { currentNav = AppNavigation.SERVICES; selectedCategory = null }
                currentNav == AppNavigation.PROMPTS -> { currentNav = AppNavigation.CATEGORIES; selectedCategory = null }
                currentNav == AppNavigation.CATEGORIES -> { currentNav = AppNavigation.SERVICES; selectedService = null }
            }
        }

        // Datos según navegación
        val allServices by database.serviceDao().getAllServices().collectAsState(initial = emptyList())
        val categoriesForService by remember(selectedService) {
            selectedService?.let { database.categoryDao().getCategoriesForService(it.id) } ?: flowOf(emptyList())
        }.collectAsState(initial = emptyList())
        val promptsForCategory by remember(selectedCategory) {
            selectedCategory?.let { database.promptDao().getPromptsForCategory(it.id) } ?: flowOf(emptyList())
        }.collectAsState(initial = emptyList())
        val searchResults by database.promptDao().searchPrompts(searchQuery).collectAsState(initial = emptyList())
        val drafts by database.draftPromptDao().getAllDrafts().collectAsState(initial = emptyList())

        // Título dinámico
        val title = when(currentNav) {
            AppNavigation.SERVICES -> "MyPromts"
            AppNavigation.CATEGORIES -> selectedService?.name ?: "Categorías"
            AppNavigation.PROMPTS -> selectedCategory?.name ?: "Prompts"
            AppNavigation.DRAFTS -> "Borradores"
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { 
                        Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) 
                    },
                    navigationIcon = {
                        if (currentNav != AppNavigation.SERVICES) {
                            IconButton(onClick = {
                                when(currentNav) {
                                    AppNavigation.CATEGORIES -> {
                                        currentNav = AppNavigation.SERVICES
                                        selectedService = null
                                    }
                                    AppNavigation.PROMPTS -> {
                                        currentNav = AppNavigation.CATEGORIES
                                        selectedCategory = null
                                    }
                                    AppNavigation.DRAFTS -> {
                                        currentNav = AppNavigation.SERVICES
                                    }
                                    else -> {}
                                }
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    },
                    actions = {
                        if (drafts.isNotEmpty() && currentNav != AppNavigation.DRAFTS) {
                            IconButton(onClick = { currentNav = AppNavigation.DRAFTS }) {
                                BadgedBox(badge = { Badge { Text(drafts.size.toString()) } }) {
                                    Icon(Icons.Default.Menu, "Borradores", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                        // Botón + para agregar según contexto
                        if (currentNav != AppNavigation.DRAFTS) {
                            IconButton(onClick = {
                                if (currentNav == AppNavigation.SERVICES) {
                                    showQuickPromptDialog = true
                                } else {
                                    showAddDialog = true
                                }
                            }) {
                                Icon(Icons.Default.Add, "Agregar", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        // Configuración
                        IconButton(onClick = { showSettingsPanel = true }) {
                            Icon(Icons.Default.Settings, "Configuración", tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { checkPermissionAndStart() },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Settings, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Activar Teclado")
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
            ) {
                // Buscador (solo en servicios)
                if (currentNav == AppNavigation.SERVICES) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Buscar prompts...") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Contenido según navegación
                when(currentNav) {
                    AppNavigation.SERVICES -> {
                        if (searchQuery.isNotEmpty()) {
                            // Resultados de búsqueda
                            Text("Resultados:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (searchResults.isEmpty()) {
                                    item {
                                        EmptyState(icon = Icons.Default.Search, message = "No hay resultados para \"$searchQuery\"")
                                    }
                                }
                                items(searchResults) { prompt ->
                                    PromptCard(
                                        prompt = prompt,
                                        onToggleFavorite = { scope.launch { database.promptDao().updatePrompt(prompt.copy(isFavorite = !prompt.isFavorite)); BackupManager.autoBackup(context, database) } },
                                        onDelete = { scope.launch { database.promptDao().deletePrompt(prompt); BackupManager.autoBackup(context, database) }; Toast.makeText(context, "Eliminado", Toast.LENGTH_SHORT).show() },
                                        onEdit = { promptToEdit = prompt; showEditDialog = true },
                                        onDuplicate = { scope.launch { database.promptDao().insertPrompt(prompt.copy(id = 0, title = prompt.title + " (copia)")); BackupManager.autoBackup(context, database) }; Toast.makeText(context, "Duplicado", Toast.LENGTH_SHORT).show() }
                                    )
                                }
                                item { Spacer(modifier = Modifier.height(100.dp)) }
                            }
                        } else {
                            // Lista de servicios
                            Text("Servicios:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (allServices.isEmpty()) {
                                    item(span = { GridItemSpan(2) }) {
                                        EmptyState(icon = Icons.Default.Star, message = "No hay servicios.\nUsa ⚙️ para crear uno.")
                                    }
                                }
                                items(allServices) { service ->
                                    ServiceCard(
                                        service = service,
                                        onClick = {
                                            selectedService = service
                                            currentNav = AppNavigation.CATEGORIES
                                            searchQuery = "" // Resetear búsqueda
                                        },
                                        onLongClick = {
                                            showEditServiceDialog = service
                                        }
                                    )
                                }
                                item(span = { GridItemSpan(2) }) { Spacer(modifier = Modifier.height(100.dp)) }
                            }
                        }
                    }
                    AppNavigation.CATEGORIES -> {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (categoriesForService.isEmpty()) {
                                item {
                                    EmptyState(icon = Icons.Default.Info, message = "No hay categorías.\nToca + para crear una.")
                                }
                            }
                            items(categoriesForService) { category ->
                                CategoryCard(
                                    category = category,
                                    onClick = {
                                        selectedCategory = category
                                        currentNav = AppNavigation.PROMPTS
                                    }
                                )
                            }
                            item { Spacer(modifier = Modifier.height(100.dp)) }
                        }
                    }
                    AppNavigation.PROMPTS -> {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (promptsForCategory.isEmpty()) {
                                item {
                                    EmptyState(icon = Icons.Default.Edit, message = "No hay prompts.\nToca + para crear uno.")
                                }
                            }
                            items(promptsForCategory) { prompt ->
                                PromptCard(
                                    prompt = prompt,
                                    onToggleFavorite = { scope.launch { database.promptDao().updatePrompt(prompt.copy(isFavorite = !prompt.isFavorite)); BackupManager.autoBackup(context, database) } },
                                    onDelete = { scope.launch { database.promptDao().deletePrompt(prompt); BackupManager.autoBackup(context, database) }; Toast.makeText(context, "Eliminado", Toast.LENGTH_SHORT).show() },
                                    onEdit = { promptToEdit = prompt; showEditDialog = true },
                                    onDuplicate = { scope.launch { database.promptDao().insertPrompt(prompt.copy(id = 0, title = prompt.title + " (copia)")); BackupManager.autoBackup(context, database) }; Toast.makeText(context, "Duplicado", Toast.LENGTH_SHORT).show() }
                                )
                            }
                            item { Spacer(modifier = Modifier.height(100.dp)) }
                        }
                    }
                    AppNavigation.DRAFTS -> {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (drafts.isEmpty()) {
                                item {
                                    EmptyState(icon = Icons.Default.Check, message = "No hay borradores\\n¡Todo ordenado!")
                                }
                            }
                            items(drafts) { draft ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    modifier = Modifier.fillMaxWidth().clickable { 
                                        draftToSave = draft
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text(draft.content, maxLines = 2, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                                        IconButton(onClick = { scope.launch { database.draftPromptDao().deleteDraft(draft) } }) {
                                            Icon(Icons.Default.Delete, "Borrar", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                            item { Spacer(modifier = Modifier.height(100.dp)) }
                        }
                    }
                }
            }
        }

        // --- DIÁLOGO AGREGAR (Categoría o Prompt según contexto) ---
        if (showAddDialog) {
            when(currentNav) {
                AppNavigation.CATEGORIES -> {
                    AddCategoryDialog(
                        onDismiss = { showAddDialog = false },
                        onSave = { name ->
                            scope.launch {
                                selectedService?.let { 
                                    database.categoryDao().insertCategory(CategoryEntity(name = name, serviceId = it.id))
                                    Toast.makeText(context, "Categoría creada", Toast.LENGTH_SHORT).show()
                                }
                                showAddDialog = false
                            }
                        }
                    )
                }
                AppNavigation.PROMPTS -> {
                    AddPromptDialog(
                        onDismiss = { showAddDialog = false },
                        onSave = { title, content ->
                            scope.launch {
                                selectedCategory?.let {
                                    database.promptDao().insertPrompt(PromptEntity(title = title, content = content, categoryId = it.id))
                                    BackupManager.autoBackup(context, database)
                                    Toast.makeText(context, "Prompt guardado", Toast.LENGTH_SHORT).show()
                                }
                                showAddDialog = false
                            }
                        }
                    )
                }
                else -> { showAddDialog = false }
            }
        }

        // --- PANEL DE CONFIGURACIÓN ---
        if (showSettingsPanel) {
            SettingsPanel(
                database = database,
                onDismiss = { showSettingsPanel = false }
            )
        }

        // --- DIALOGO DE EDICIÓN ---
        if (showEditDialog && promptToEdit != null) {
            EditPromptDialog(
                prompt = promptToEdit!!,
                onDismiss = { showEditDialog = false },
                onSave = { t, c ->
                    scope.launch {
                        database.promptDao().updatePrompt(promptToEdit!!.copy(title = t, content = c))
                        BackupManager.autoBackup(context, database)
                        showEditDialog = false
                    }
                }
            )
        }

        // --- DIALOGO GUARDAR BORRADOR ---
        if (draftToSave != null) {
            SaveDraftDialog(
                draft = draftToSave!!,
                database = database,
                onDismiss = { draftToSave = null },
                onSave = { categoryId, t, c ->
                    scope.launch {
                        database.promptDao().insertPrompt(PromptEntity(title = t, content = c, categoryId = categoryId))
                        database.draftPromptDao().deleteDraft(draftToSave!!)
                        BackupManager.autoBackup(context, database)
                        draftToSave = null
                        Toast.makeText(context, "Guardado correctamente", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        // --- DIALOGO QUICK PROMPT ---
        if (showQuickPromptDialog) {
            QuickPromptDialog(
                database = database,
                onDismiss = { showQuickPromptDialog = false },
                onSave = { categoryId, t, c ->
                    scope.launch {
                        database.promptDao().insertPrompt(PromptEntity(title = t, content = c, categoryId = categoryId))
                        BackupManager.autoBackup(context, database)
                        showQuickPromptDialog = false
                        Toast.makeText(context, "Prompt Creado", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        // --- DIALOGO EDITAR SERVICIO ---
        if (showEditServiceDialog != null) {
            EditServiceDialog(
                service = showEditServiceDialog!!,
                database = database,
                allServices = allServices,
                onDismiss = { showEditServiceDialog = null },
                onServiceDeleted = {
                    showEditServiceDialog = null
                    selectedService = null
                    currentNav = AppNavigation.SERVICES
                }
            )
        }
    }

    // ============= COMPONENTES =============

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun ServiceCard(service: ServiceEntity, onClick: () -> Unit, onLongClick: () -> Unit) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
            elevation = CardDefaults.cardElevation(4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                // Icono del servicio
                AsyncImage(
                    model = File(service.iconIdentifier),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = service.name,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    @Composable
    fun CategoryCard(category: CategoryEntity, onClick: () -> Unit) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() },
            elevation = CardDefaults.cardElevation(2.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Default.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    @Composable
    fun PromptCard(prompt: PromptEntity, onToggleFavorite: () -> Unit, onDelete: () -> Unit, onEdit: () -> Unit, onDuplicate: () -> Unit) {
        var isExpanded by remember { mutableStateOf(false) }
        
        Card(
            elevation = CardDefaults.cardElevation(0.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Título + Favorito (siempre visible, no se mueve)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        prompt.title, 
                        style = MaterialTheme.typography.titleSmall, 
                        fontWeight = FontWeight.Bold, 
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onToggleFavorite, modifier = Modifier.size(28.dp)) {
                        Icon(
                            if (prompt.isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder, 
                            null, 
                            tint = if (prompt.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, 
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                
                // Contenido (preview o completo según expansión)
                AnimatedContent(
                    targetState = isExpanded,
                    transitionSpec = {
                        fadeIn() togetherWith fadeOut()
                    },
                    label = "contentAnim"
                ) { expanded ->
                    if (expanded) {
                        Column {
                            Spacer(Modifier.height(6.dp))
                            Text(prompt.content, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                IconButton(onClick = onDuplicate, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Add, "Duplicar", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                }
                                IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Edit, "Editar", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                }
                                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Delete, "Borrar", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    } else {
                        Text(prompt.content, style = MaterialTheme.typography.bodySmall, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    @Composable
    fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, message: String) {
        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    // ============= DIÁLOGOS =============

    @Composable
    fun AddCategoryDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Nueva Categoría", color = MaterialTheme.colorScheme.primary) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = { if (name.isNotEmpty()) onSave(name) },
                    enabled = name.isNotEmpty()
                ) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    @Composable
    fun AddPromptDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
        var title by remember { mutableStateOf("") }
        var content by remember { mutableStateOf("") }
        val scrollState = rememberScrollState()

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Nuevo Prompt", color = MaterialTheme.colorScheme.primary) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(scrollState)
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Título") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text("Contenido") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 5,
                        maxLines = 10
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { if (title.isNotEmpty() && content.isNotEmpty()) onSave(title, content) },
                    enabled = title.isNotEmpty() && content.isNotEmpty()
                ) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    @Composable
    fun EditPromptDialog(prompt: PromptEntity, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
        var title by remember { mutableStateOf(prompt.title) }
        var content by remember { mutableStateOf(prompt.content) }
        val scrollState = rememberScrollState()

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Editar Prompt", color = MaterialTheme.colorScheme.primary) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(scrollState)
                ) {
                    OutlinedTextField(title, { title = it }, label = { Text("Título") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(content, { content = it }, label = { Text("Contenido") }, minLines = 5, maxLines = 10, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { Button(onClick = { onSave(title, content) }) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun SaveDraftDialog(
        draft: com.imlegendco.mypromts.database.DraftPromptEntity,
        database: AppDatabase,
        onDismiss: () -> Unit,
        onSave: (Int, String, String) -> Unit
    ) {
        val services by database.serviceDao().getAllServices().collectAsState(initial = emptyList())
        var selectedService by remember { mutableStateOf<ServiceEntity?>(null) }
        
        val categories by if (selectedService != null) {
            database.categoryDao().getCategoriesForService(selectedService!!.id).collectAsState(initial = emptyList())
        } else {
            remember { mutableStateOf(emptyList()) }
        }
        var selectedCategory by remember { mutableStateOf<CategoryEntity?>(null) }
        
        var title by remember { mutableStateOf("") }
        var content by remember { mutableStateOf(draft.content) }

        var serviceExpanded by remember { mutableStateOf(false) }
        var categoryExpanded by remember { mutableStateOf(false) }

        val scrollState = rememberScrollState()

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Mover a Prompts", color = MaterialTheme.colorScheme.primary) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(scrollState)
                ) {
                    ExposedDropdownMenuBox(
                        expanded = serviceExpanded,
                        onExpandedChange = { serviceExpanded = !serviceExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedService?.name ?: "Seleccionar un Servicio",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Servicio") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = serviceExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                        )
                        ExposedDropdownMenu(
                            expanded = serviceExpanded,
                            onDismissRequest = { serviceExpanded = false }
                        ) {
                            services.forEach { s ->
                                DropdownMenuItem(
                                    text = { Text(s.name) },
                                    onClick = {
                                        selectedService = s
                                        selectedCategory = null
                                        serviceExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    ExposedDropdownMenuBox(
                        expanded = categoryExpanded,
                        onExpandedChange = { categoryExpanded = !categoryExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedCategory?.name ?: "Seleccionar una Categoría",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Categoría") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            enabled = selectedService != null,
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                        )
                        ExposedDropdownMenu(
                            expanded = categoryExpanded,
                            onDismissRequest = { categoryExpanded = false }
                        ) {
                            categories.forEach { c ->
                                DropdownMenuItem(
                                    text = { Text(c.name) },
                                    onClick = {
                                        selectedCategory = c
                                        categoryExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(title, { title = it }, label = { Text("Título") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(content, { content = it }, label = { Text("Contenido") }, minLines = 5, maxLines = 10, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (title.isNotEmpty() && content.isNotEmpty() && selectedCategory != null) {
                            onSave(selectedCategory!!.id, title, content)
                        }
                    },
                    enabled = title.isNotEmpty() && content.isNotEmpty() && selectedCategory != null
                ) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun QuickPromptDialog(
        database: AppDatabase,
        onDismiss: () -> Unit,
        onSave: (Int, String, String) -> Unit
    ) {
        val services by database.serviceDao().getAllServices().collectAsState(initial = emptyList())
        var selectedService by remember { mutableStateOf<ServiceEntity?>(null) }
        
        val categories by if (selectedService != null) {
            database.categoryDao().getCategoriesForService(selectedService!!.id).collectAsState(initial = emptyList())
        } else {
            remember { mutableStateOf(emptyList()) }
        }
        var selectedCategory by remember { mutableStateOf<CategoryEntity?>(null) }
        
        var title by remember { mutableStateOf("") }
        var content by remember { mutableStateOf("") }

        var serviceExpanded by remember { mutableStateOf(false) }
        var categoryExpanded by remember { mutableStateOf(false) }

        val scrollState = rememberScrollState()

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Nuevo Prompt Rápido", color = MaterialTheme.colorScheme.primary) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(scrollState)
                ) {
                    ExposedDropdownMenuBox(
                        expanded = serviceExpanded,
                        onExpandedChange = { serviceExpanded = !serviceExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedService?.name ?: "Seleccionar un Servicio",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Servicio") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = serviceExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                        )
                        ExposedDropdownMenu(
                            expanded = serviceExpanded,
                            onDismissRequest = { serviceExpanded = false }
                        ) {
                            services.forEach { s ->
                                DropdownMenuItem(
                                    text = { Text(s.name) },
                                    onClick = {
                                        selectedService = s
                                        selectedCategory = null
                                        serviceExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    ExposedDropdownMenuBox(
                        expanded = categoryExpanded,
                        onExpandedChange = { categoryExpanded = !categoryExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedCategory?.name ?: "Seleccionar una Categoría",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Categoría") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            enabled = selectedService != null,
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                        )
                        ExposedDropdownMenu(
                            expanded = categoryExpanded,
                            onDismissRequest = { categoryExpanded = false }
                        ) {
                            categories.forEach { c ->
                                DropdownMenuItem(
                                    text = { Text(c.name) },
                                    onClick = {
                                        selectedCategory = c
                                        categoryExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(title, { title = it }, label = { Text("Título") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(content, { content = it }, label = { Text("Contenido") }, minLines = 5, maxLines = 10, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (title.isNotEmpty() && content.isNotEmpty() && selectedCategory != null) {
                            onSave(selectedCategory!!.id, title, content)
                        }
                    },
                    enabled = title.isNotEmpty() && content.isNotEmpty() && selectedCategory != null
                ) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun EditServiceDialog(
        service: ServiceEntity,
        database: AppDatabase,
        allServices: List<ServiceEntity>,
        onDismiss: () -> Unit,
        onServiceDeleted: () -> Unit
    ) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var newName by remember { mutableStateOf(service.name) }
        var newIconUri by remember { mutableStateOf<android.net.Uri?>(null) }

        val photoPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia(),
            onResult = { uri -> newIconUri = uri }
        )
        
        val currentIndex = allServices.indexOfFirst { it.id == service.id }
        
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Editar Servicio", color = MaterialTheme.colorScheme.primary) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            .clickable { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (newIconUri != null) {
                            AsyncImage(model = newIconUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        } else {
                            AsyncImage(model = File(service.iconIdentifier), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Nombre del Servicio") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text("Reordenar", style = MaterialTheme.typography.labelMedium)
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(
                            onClick = {
                                if (currentIndex > 0) {
                                    val swapWith = allServices[currentIndex - 1]
                                    scope.launch {
                                        database.serviceDao().insertService(service.copy(orderIndex = swapWith.orderIndex))
                                        database.serviceDao().insertService(swapWith.copy(orderIndex = service.orderIndex))
                                    }
                                }
                            },
                            enabled = currentIndex > 0
                        ) { Icon(Icons.Default.KeyboardArrowUp, "Mover Arriba") }
                        
                        IconButton(
                            onClick = {
                                if (currentIndex < allServices.size - 1) {
                                    val swapWith = allServices[currentIndex + 1]
                                    scope.launch {
                                        database.serviceDao().insertService(service.copy(orderIndex = swapWith.orderIndex))
                                        database.serviceDao().insertService(swapWith.copy(orderIndex = service.orderIndex))
                                    }
                                }
                            },
                            enabled = currentIndex < allServices.size - 1
                        ) { Icon(Icons.Default.KeyboardArrowDown, "Mover Abajo") }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                database.serviceDao().deleteService(service)
                                Toast.makeText(context, "Servicio eliminado", Toast.LENGTH_SHORT).show()
                                onServiceDeleted()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Eliminar Servicio") }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val newPath = if (newIconUri != null) ImageStorageManager.saveImageToInternalStorage(context, newIconUri!!) else service.iconIdentifier
                            database.serviceDao().insertService(service.copy(name = newName, iconIdentifier = newPath))
                            BackupManager.autoBackup(context, database)
                            Toast.makeText(context, "Servicio actualizado", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    },
                    enabled = newName.isNotEmpty()
                ) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // ============= PANEL DE CONFIGURACIÓN (SIN TABS) =============

    @Composable
    fun SettingsPanel(database: AppDatabase, onDismiss: () -> Unit) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        
        // Estado para crear servicio
        var newServiceName by remember { mutableStateOf("") }
        var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
        val photoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { selectedImageUri = it }
        
        // Estado para borrar
        var showDeleteSection by remember { mutableStateOf(false) }
        val allServices by database.serviceDao().getAllServices().collectAsState(initial = emptyList())
        
        // Estado para backup
        var isExporting by remember { mutableStateOf(false) }
        var isImporting by remember { mutableStateOf(false) }
        var backupFiles by remember { mutableStateOf(BackupManager.listBackups()) }

        val jsonPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                isImporting = true
                scope.launch {
                    try {
                        val inputStream = context.contentResolver.openInputStream(uri)
                        if (inputStream != null) {
                            val content = inputStream.bufferedReader().use { it.readText() }
                            val result = BackupManager.importBackup(context, database, content)
                            isImporting = false
                            result.onSuccess { count ->
                                Toast.makeText(context, "✅ Importados $count elementos", Toast.LENGTH_SHORT).show()
                            }.onFailure { e ->
                                Toast.makeText(context, "❌ Error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            isImporting = false
                            Toast.makeText(context, "❌ No se pudo leer el archivo", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        isImporting = false
                        Toast.makeText(context, "❌ Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        ModalBottomSheet(
            onDismissRequest = onDismiss,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Configuración", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(24.dp))

                // === CREAR SERVICIO ===
                Text("➕ Crear Servicio", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            .clickable { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selectedImageUri != null) {
                            AsyncImage(model = selectedImageUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        } else {
                            Icon(Icons.Default.Add, "Foto", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    OutlinedTextField(
                        value = newServiceName,
                        onValueChange = { newServiceName = it },
                        label = { Text("Nombre") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = {
                            if (newServiceName.isNotEmpty() && selectedImageUri != null) {
                                scope.launch {
                                    val path = ImageStorageManager.saveImageToInternalStorage(context, selectedImageUri!!)
                                    val maxOrder = database.serviceDao().getMaxOrderIndex() ?: 0
                                    database.serviceDao().insertService(ServiceEntity(name = newServiceName, iconIdentifier = path, orderIndex = maxOrder + 1))
                                    Toast.makeText(context, "Servicio creado", Toast.LENGTH_SHORT).show()
                                    newServiceName = ""; selectedImageUri = null
                                }
                            } else Toast.makeText(context, "Falta nombre o imagen", Toast.LENGTH_SHORT).show()
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Check, "Guardar")
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(16.dp))

                // === BACKUP ===
                Text("💾 Backup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            isExporting = true
                            scope.launch {
                                val result = BackupManager.exportBackup(context, database)
                                isExporting = false
                                result.onSuccess { path ->
                                    Toast.makeText(context, "✅ Guardado en:\n$path", Toast.LENGTH_LONG).show()
                                    backupFiles = BackupManager.listBackups()
                                }.onFailure { e ->
                                    Toast.makeText(context, "❌ Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = !isExporting,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isExporting) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        else Icon(Icons.Default.Share, null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Exportar")
                    }
                    Button(
                        onClick = {
                            jsonPickerLauncher.launch("application/json") // O "*/*" si "application/json" da problemas
                        },
                        enabled = !isImporting,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        if (isImporting) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        else Icon(Icons.Default.Add, null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Importar")
                    }
                }

                if (backupFiles.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Restaurar desde:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    backupFiles.take(3).forEach { file ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(file.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            TextButton(
                                onClick = {
                                    isImporting = true
                                    scope.launch {
                                        try {
                                            val content = file.readText()
                                            val result = BackupManager.importBackup(context, database, content)
                                            isImporting = false
                                            result.onSuccess { count ->
                                                Toast.makeText(context, "✅ Importados $count elementos", Toast.LENGTH_SHORT).show()
                                            }.onFailure { e ->
                                                Toast.makeText(context, "❌ Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (e: Exception) {
                                            isImporting = false
                                            Toast.makeText(context, "❌ Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                enabled = !isImporting
                            ) { Text("Restaurar") }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(24.dp))

            // Notificación Permanente
            Text(
                "Configuración",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Notificación Permanente",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Muestra una notificación fija para cambiar rápidamente de teclado en cualquier app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                androidx.compose.material3.Switch(
                    checked = isNotificationEnabled,
                    onCheckedChange = { isChecked ->
                        if (isChecked) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                if (ContextCompat.checkSelfPermission(
                                        this@MainActivity,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    isNotificationEnabled = true
                                    saveNotificationPreference(true)
                                    KeyboardSwitcherService.start(this@MainActivity)
                                } else {
                                    requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            } else {
                                isNotificationEnabled = true
                                saveNotificationPreference(true)
                                KeyboardSwitcherService.start(this@MainActivity)
                            }
                        } else {
                            isNotificationEnabled = false
                            saveNotificationPreference(false)
                            KeyboardSwitcherService.stop(this@MainActivity)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(24.dp))
            
            // Selector Rápido de Teclado
            Button(
                onClick = { 
                    val imm = this@MainActivity.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    imm.showInputMethodPicker()
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Icon(Icons.Filled.Settings, contentDescription = "Teclados", tint = MaterialTheme.colorScheme.onSecondary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Elegir Teclado (Selector Rápido)", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(32.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(24.dp))

                // === FIRMA ===
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Desarrollado por: Gemini", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("Vibecodeado por: @ElWaiELe", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("Hecho con ✨ en Antigravity", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("Powered by Google", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Versión: 2.0.0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    private fun checkPermissionAndStart() {
        val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.showInputMethodPicker()
    }

    private fun handleKeyboardActivationShortcut() {
        checkPermissionAndStart()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == "com.imlegendco.mypromts.ACTION_ACTIVATE_KEYBOARD") {
            handleKeyboardActivationShortcut()
        }
    }

    // Funciones Helper para Guardar Preferencias de Notificación
    private fun saveNotificationPreference(enabled: Boolean) {
        val sharedPrefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().putBoolean("keyboard_notification_enabled", enabled).apply()
    }

    private fun getNotificationPreference(): Boolean {
        val sharedPrefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return sharedPrefs.getBoolean("keyboard_notification_enabled", false)
    }
}