package com.imlegendco.mypromts

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.imlegendco.mypromts.database.AppDatabase
import com.imlegendco.mypromts.database.DraftPromptEntity
import kotlinx.coroutines.launch

class SavePromptActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()

        if (!text.isNullOrBlank()) {
            val db = AppDatabase.getDatabase(this)
            
            // To run coroutine in standard Activity, we can use a basic thread or
            // if we want to use coroutines here, we can use an unmanaged scope, 
            // but for simplicity and safety without ComponentActivity we use a new thread or runBlocking.
            // Actually, we can just extend ComponentActivity instead of Activity:
            
            Thread {
                kotlin.runCatching {
                    val dao = db.draftPromptDao()
                    // use runBlocking if we must, or just a suspended call run in a background thread using runBlocking
                    kotlinx.coroutines.runBlocking {
                        dao.insertDraft(DraftPromptEntity(content = text))
                    }
                }.onSuccess {
                    runOnUiThread {
                        Toast.makeText(this@SavePromptActivity, "Guardado en MyPromts", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }.onFailure {
                    runOnUiThread {
                        Toast.makeText(this@SavePromptActivity, "Error al guardar: ${it.message}", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }.start()
        } else {
            finish()
        }
    }
}
