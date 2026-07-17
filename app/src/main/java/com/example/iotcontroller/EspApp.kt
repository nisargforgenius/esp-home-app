package com.example.iotcontroller

import android.app.Application
import com.example.iotcontroller.data.EspRepository

class EspApp : Application() {
    lateinit var repository: EspRepository
    
    override fun onCreate() {
        super.onCreate()
        repository = EspRepository(this)
    }
}
