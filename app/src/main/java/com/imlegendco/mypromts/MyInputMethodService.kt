package com.imlegendco.mypromts

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.os.IBinder
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import coil.compose.AsyncImage
import com.imlegendco.mypromts.database.AppDatabase
import com.imlegendco.mypromts.database.PromptEntity
import kotlinx.coroutines.launch
import java.io.File
import android.view.inputmethod.InputMethodManager

enum class NavView { SERVICES, CATEGORIES, PROMPTS, FAVORITES }

class MyInputMethodService : InputMethodService(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }

    override fun onCreateInputView(): View {
        val composeView = ComposeView(this).apply {
            // Attach lifecycle owners to the ComposeView itself
            setViewTreeLifecycleOwner(this@MyInputMethodService)
            setViewTreeSavedStateRegistryOwner(this@MyInputMethodService)
            setViewTreeViewModelStoreOwner(this@MyInputMethodService)

            setContent {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    KeyboardUI()
                }
            }
        }
        
        // Also attach to the window decor view, where Compose sometimes looks in a Dialog/IMS
        window?.window?.decorView?.let { decorView ->
            decorView.setViewTreeLifecycleOwner(this)
            decorView.setViewTreeSavedStateRegistryOwner(this)
            decorView.setViewTreeViewModelStoreOwner(this)
        }
        
        return composeView
    }

    @OptIn(ExperimentalAnimationApi::class)
    @Composable
    fun KeyboardUI() {
        val database = remember { AppDatabase.getDatabase(this) }
        val scope = rememberCoroutineScope()

        var currentView by remember { mutableStateOf(NavView.SERVICES) }
        var selectedServiceId by remember { mutableIntStateOf(-1) }
        var selectedCategoryId by remember { mutableIntStateOf(-1) }

        fun insertPrompt(prompt: PromptEntity) {
            val ic = currentInputConnection
            if (ic != null) {
                ic.commitText(prompt.content, 1)
                scope.launch {
                    database.promptDao().updatePrompt(prompt.copy(lastUsed = System.currentTimeMillis()))
                }
            } else {
                Toast.makeText(this@MyInputMethodService, "No se puede escribir aquí", Toast.LENGTH_SHORT).show()
            }
            // Optionally, switch back to previous IME after inserting
            // switchInputMethod(Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD))
        }

        fun toggleFavorite(prompt: PromptEntity) {
            scope.launch {
                database.promptDao().updatePrompt(prompt.copy(isFavorite = !prompt.isFavorite))
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(350.dp) // Fixed height for the keyboard area
                .background(Color(0xFF121212))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentView != NavView.SERVICES) {
                        IconButton(onClick = {
                            when (currentView) {
                                NavView.CATEGORIES -> currentView = NavView.SERVICES
                                NavView.PROMPTS -> currentView = NavView.CATEGORIES
                                NavView.FAVORITES -> currentView = NavView.SERVICES
                                else -> {}
                            }
                        }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }
                    } else {
                        IconButton(onClick = { currentView = NavView.FAVORITES }) {
                            Icon(Icons.Default.Favorite, null, tint = Color(0xFFFF00FF))
                        }
                    }
                    Text(
                        text = when (currentView) {
                            NavView.SERVICES -> "MyPromts Keyboard"
                            NavView.CATEGORIES -> "Categorías"
                            NavView.PROMPTS -> "Prompts"
                            NavView.FAVORITES -> "Favoritos"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = {
                        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.showInputMethodPicker()
                    }) {
                        Icon(Icons.Default.Close, "Cambiar teclado", tint = Color.White)
                    }
                }
                Spacer(Modifier.height(8.dp))

                // Contenido según navegación
                AnimatedContent(
                    targetState = currentView,
                    transitionSpec = {
                        slideInHorizontally(initialOffsetX = { it }) togetherWith slideOutHorizontally(targetOffsetX = { -it })
                    },
                    label = "keyboardNav"
                ) { view ->
                    when (view) {
                        NavView.SERVICES -> {
                            val services by database.serviceDao().getAllServices().collectAsState(initial = emptyList())
                            val recents by database.promptDao().getRecentPrompts().collectAsState(initial = emptyList())
                            
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (recents.isNotEmpty()) {
                                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                                        Column {
                                            Text("Recientes", color = Color.Gray, style = MaterialTheme.typography.labelMedium)
                                            Spacer(Modifier.height(8.dp))
                                            LazyColumn(Modifier.heightIn(max = 120.dp)) {
                                                items(recents) { PromptCard(it, ::insertPrompt, ::toggleFavorite) }
                                            }
                                            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color.DarkGray)
                                        }
                                    }
                                }
                                items(services) { s ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                                        modifier = Modifier.aspectRatio(1f).clickable {
                                            selectedServiceId = s.id; currentView = NavView.CATEGORIES
                                        },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        AsyncImage(
                                            model = File(s.iconIdentifier),
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop,
                                            error = rememberVectorPainter(Icons.Default.Star),
                                            placeholder = rememberVectorPainter(Icons.Default.Star)
                                        )
                                    }
                                }
                            }
                        }
                        NavView.CATEGORIES -> {
                            val cats by database.categoryDao().getCategoriesForService(selectedServiceId).collectAsState(initial = emptyList())
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(cats) { c ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            selectedCategoryId = c.id; currentView = NavView.PROMPTS
                                        },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Box(Modifier.size(10.dp).background(Color(0xFFFF00FF), CircleShape))
                                            Spacer(Modifier.width(16.dp))
                                            Text(c.name, color = Color.White, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                }
                            }
                        }
                        NavView.PROMPTS -> {
                            val prompts by database.promptDao().getPromptsForCategory(selectedCategoryId).collectAsState(initial = emptyList())
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(prompts) { PromptCard(it, ::insertPrompt, ::toggleFavorite) }
                            }
                        }
                        NavView.FAVORITES -> {
                            val favs by database.promptDao().getFavoritePrompts().collectAsState(initial = emptyList())
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(favs) { PromptCard(it, ::insertPrompt, ::toggleFavorite) }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun PromptCard(prompt: PromptEntity, onInsert: (PromptEntity) -> Unit, onFavorite: (PromptEntity) -> Unit) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2C)),
            modifier = Modifier.fillMaxWidth().clickable { onInsert(prompt) },
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(prompt.title, color = Color(0xFFFF00FF), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(prompt.content, color = Color.LightGray, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
                IconButton(onClick = { onFavorite(prompt) }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (prompt.isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        null,
                        tint = if (prompt.isFavorite) Color(0xFFFF00FF) else Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
