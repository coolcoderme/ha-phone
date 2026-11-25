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
        super.onCreate()
        instance = this
        prefs = Prefs(this)
        db = AppDatabase.create(this)
        repo = HubRepository(db, prefs)
    }

    companion object {
        lateinit var instance: HaPhoneApp
            private set
    }
}
