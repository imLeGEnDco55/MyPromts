package com.imlegendco.mypromts

import android.app.Activity
import android.os.Bundle
import android.view.inputmethod.InputMethodManager

class ActivateKeyboardActivity : Activity() {

    private var isPicking = false
    private var hasChosen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Give the OS a moment to retract the notification shade before showing the dialog
        window.decorView.postDelayed({
            if (!isFinishing && !isPicking && !hasChosen) {
                launchPicker()
            }
        }, 300) // Slightly longer delay helps on some Xiaomi devices
    }

    override fun onResume() {
        super.onResume()
        // If we resumed but haven't started picking and aren't waiting for the delay
        // Also a fallback if it hangs (e.g. Activity recreated)
        window.decorView.postDelayed({
            if (!isFinishing && !isPicking && !hasChosen) {
                launchPicker()
            }
        }, 300)
    }

    private fun launchPicker() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showInputMethodPicker()
        isPicking = true
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus && isPicking) {
            // The picker dialog dropped in and took focus
            hasChosen = true
            isPicking = false
        } else if (hasFocus && hasChosen) {
            // Focus returned to us = user made a choice or cancelled
            finish()
        }
    }
}
