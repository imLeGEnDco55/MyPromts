package com.imlegendco.mypromts.utils

import android.content.Context
import android.os.Environment
import com.imlegendco.mypromts.database.AppDatabase
import com.imlegendco.mypromts.database.CategoryEntity
import com.imlegendco.mypromts.database.PromptEntity
import com.imlegendco.mypromts.database.ServiceEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class BackupData(
    val services: List<ServiceEntity>,
    val categories: List<CategoryEntity>,
    val prompts: List<PromptEntity>
)

object BackupManager {
    
    private const val BACKUP_FOLDER = "MyPromts_Backups"
    
    /**
     * Exporta toda la base de datos a un archivo JSON en Downloads
     */
    suspend fun exportBackup(context: Context, database: AppDatabase): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                // Obtener todos los datos
                val services = database.serviceDao().getAllServices().first()
                val allCategories = mutableListOf<CategoryEntity>()
                val allPrompts = mutableListOf<PromptEntity>()
                
                services.forEach { service ->
                    val cats = database.categoryDao().getCategoriesForService(service.id).first()
                    allCategories.addAll(cats)
                    cats.forEach { cat ->
                        val prompts = database.promptDao().getPromptsForCategory(cat.id).first()
                        allPrompts.addAll(prompts)
                    }
                }
                
                // Crear JSON
                val json = JSONObject().apply {
                    put("version", 1)
                    put("exportDate", System.currentTimeMillis())
                    put("services", JSONArray().apply {
                        services.forEach { s ->
                            put(JSONObject().apply {
                                put("id", s.id)
                                put("name", s.name)
                                put("iconIdentifier", s.iconIdentifier)
                            })
                        }
                    })
                    put("categories", JSONArray().apply {
                        allCategories.forEach { c ->
                            put(JSONObject().apply {
                                put("id", c.id)
                                put("name", c.name)
                                put("serviceId", c.serviceId)
                            })
                        }
                    })
                    put("prompts", JSONArray().apply {
                        allPrompts.forEach { p ->
                            put(JSONObject().apply {
                                put("id", p.id)
                                put("title", p.title)
                                put("content", p.content)
                                put("categoryId", p.categoryId)
                                put("isFavorite", p.isFavorite)
                                put("lastUsed", p.lastUsed)
                            })
                        }
                    })
                }
                
                // Guardar archivo
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val backupDir = File(downloadsDir, BACKUP_FOLDER)
                if (!backupDir.exists()) backupDir.mkdirs()
                
                val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
                val fileName = "mypromts_backup_${dateFormat.format(Date())}.json"
                val file = File(backupDir, fileName)
                
                file.writeText(json.toString(2))
                
                Result.success("${backupDir.absolutePath}/$fileName")
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * Auto-backup silencioso que sobrescribe el archivo anterior
     * Se llama automáticamente al crear/editar/borrar prompts
     */
    suspend fun autoBackup(context: Context, database: AppDatabase) {
        withContext(Dispatchers.IO) {
            try {
                val services = database.serviceDao().getAllServices().first()
                val allCategories = mutableListOf<CategoryEntity>()
                val allPrompts = mutableListOf<PromptEntity>()
                
                services.forEach { service ->
                    val cats = database.categoryDao().getCategoriesForService(service.id).first()
                    allCategories.addAll(cats)
                    cats.forEach { cat ->
                        val prompts = database.promptDao().getPromptsForCategory(cat.id).first()
                        allPrompts.addAll(prompts)
                    }
                }
                
                val json = JSONObject().apply {
                    put("version", 1)
                    put("exportDate", System.currentTimeMillis())
                    put("services", JSONArray().apply {
                        services.forEach { s ->
                            put(JSONObject().apply {
                                put("id", s.id)
                                put("name", s.name)
                                put("iconIdentifier", s.iconIdentifier)
                            })
                        }
                    })
                    put("categories", JSONArray().apply {
                        allCategories.forEach { c ->
                            put(JSONObject().apply {
                                put("id", c.id)
                                put("name", c.name)
                                put("serviceId", c.serviceId)
                            })
                        }
                    })
                    put("prompts", JSONArray().apply {
                        allPrompts.forEach { p ->
                            put(JSONObject().apply {
                                put("id", p.id)
                                put("title", p.title)
                                put("content", p.content)
                                put("categoryId", p.categoryId)
                                put("isFavorite", p.isFavorite)
                                put("lastUsed", p.lastUsed)
                            })
                        }
                    })
                }
                
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val backupDir = File(downloadsDir, BACKUP_FOLDER)
                if (!backupDir.exists()) backupDir.mkdirs()
                
                // Auto-backup siempre sobrescribe el mismo archivo
                val file = File(backupDir, "mypromts_auto_backup.json")
                file.writeText(json.toString(2))
            } catch (e: Exception) {
                // Silencioso, no interrumpir al usuario
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Importa un backup desde un archivo JSON
     */
    suspend fun importBackup(context: Context, database: AppDatabase, jsonContent: String): Result<Int> {
        return withContext(Dispatchers.IO) {
            try {
                val json = JSONObject(jsonContent)
                var importedCount = 0
                
                // Mapeo de IDs viejos a nuevos
                val serviceIdMap = mutableMapOf<Int, Int>()
                val categoryIdMap = mutableMapOf<Int, Int>()
                
                // Importar Servicios
                val servicesArray = json.getJSONArray("services")
                for (i in 0 until servicesArray.length()) {
                    val obj = servicesArray.getJSONObject(i)
                    val oldId = obj.getInt("id")
                    val service = ServiceEntity(
                        id = 0, // Auto-generate
                        name = obj.getString("name"),
                        iconIdentifier = obj.getString("iconIdentifier")
                    )
                    // Room retorna el nuevo ID al insertar
                    val newId = database.serviceDao().insertServiceReturnId(service)
                    serviceIdMap[oldId] = newId.toInt()
                    importedCount++
                }
                
                // Importar Categorías
                val categoriesArray = json.getJSONArray("categories")
                for (i in 0 until categoriesArray.length()) {
                    val obj = categoriesArray.getJSONObject(i)
                    val oldId = obj.getInt("id")
                    val oldServiceId = obj.getInt("serviceId")
                    val newServiceId = serviceIdMap[oldServiceId] ?: continue
                    
                    val category = CategoryEntity(
                        id = 0,
                        name = obj.getString("name"),
                        serviceId = newServiceId
                    )
                    val newId = database.categoryDao().insertCategory(category)
                    categoryIdMap[oldId] = newId.toInt()
                    importedCount++
                }
                
                // Importar Prompts
                val promptsArray = json.getJSONArray("prompts")
                for (i in 0 until promptsArray.length()) {
                    val obj = promptsArray.getJSONObject(i)
                    val oldCategoryId = obj.getInt("categoryId")
                    val newCategoryId = categoryIdMap[oldCategoryId] ?: continue
                    
                    val prompt = PromptEntity(
                        id = 0,
                        title = obj.getString("title"),
                        content = obj.getString("content"),
                        categoryId = newCategoryId,
                        isFavorite = obj.optBoolean("isFavorite", false),
                        lastUsed = obj.optLong("lastUsed", 0)
                    )
                    database.promptDao().insertPrompt(prompt)
                    importedCount++
                }
                
                Result.success(importedCount)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * Lista los backups disponibles en Downloads
     */
    fun listBackups(): List<File> {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val backupDir = File(downloadsDir, BACKUP_FOLDER)
        
        return if (backupDir.exists()) {
            backupDir.listFiles { file -> file.extension == "json" }?.sortedByDescending { it.lastModified() } ?: emptyList()
        } else {
            emptyList()
        }
    }
}
