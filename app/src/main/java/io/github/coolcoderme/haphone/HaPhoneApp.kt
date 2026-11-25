package io.github.coolcoderme.haphone

import android.app.Application
import io.github.coolcoderme.haphone.data.HubRepository
import io.github.coolcoderme.haphone.data.Prefs
import io.github.coolcoderme.haphone.data.db.AppDatabase

class HaPhoneApp : Application() {
    lateinit var db: AppDatabase
        private set
    lateinit var prefs: Prefs
        private set
    lateinit var repo: HubRepository
        private set

    override fun onCreate() {
        // kotlinx.coroutines 1.7+ probes java.lang.ClassValue, which Android 8 does not have.
        System.setProperty("kotlinx.coroutines.internal.useClassValue", "false")
        super.onCreate()
        instance = this
        prefs = Prefs(this)
        db = AppDatabase.create(this)
        repo = HubRepository(db, prefs)
    }

    companion object {
        init {
            System.setProperty("kotlinx.coroutines.internal.useClassValue", "false")
        }

        lateinit var instance: HaPhoneApp
            private set
    }
}
