package com.imlegendco.mypromts.database

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [ServiceEntity::class, CategoryEntity::class, PromptEntity::class, DraftPromptEntity::class],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun serviceDao(): ServiceDao
    abstract fun categoryDao(): CategoryDao
    abstract fun promptDao(): PromptDao
    abstract fun draftPromptDao(): DraftPromptDao

    private class AppDatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {

        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    populateDatabase(database.serviceDao(), database.categoryDao(), database.promptDao())
                }
            }
        }

        suspend fun populateDatabase(serviceDao: ServiceDao, categoryDao: CategoryDao, promptDao: PromptDao) {
            // Ejemplo de Pre-populación
            val service1 = ServiceEntity(name = "ChatGPT", iconIdentifier = "banana_icon")
            val service2 = ServiceEntity(name = "Claude", iconIdentifier = "blue_circle")
            val service3 = ServiceEntity(name = "MidJourney", iconIdentifier = "art_palette")
            
            serviceDao.insertService(service1)
            serviceDao.insertService(service2)
            serviceDao.insertService(service3)

            // Nota: Al insertar, Room autogenera IDs, pero aquí no los capturamos fácilmente
            // en un callback simple sin lógica extra. Para un ejemplo básico, esto basta
            // para tener Servicios base. Las Categorías y Prompts las crearemos desde la UI.
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
                    override fun migrate(database: SupportSQLiteDatabase) {
                        database.execSQL("ALTER TABLE services ADD COLUMN orderIndex INTEGER NOT NULL DEFAULT 0")
                    }
                }

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "prompt_database"
                )
                // Usamos el callback para pre-popular datos al CREAR la base de datos por primera vez
                .addCallback(AppDatabaseCallback(CoroutineScope(Dispatchers.IO)))
                .addMigrations(MIGRATION_5_6)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}