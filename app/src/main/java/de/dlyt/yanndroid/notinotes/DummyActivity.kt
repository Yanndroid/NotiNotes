package de.dlyt.yanndroid.notinotes

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.os.Bundle

class DummyActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        keyguardManager.requestDismissKeyguard(this, null)
        finish()
    }
}