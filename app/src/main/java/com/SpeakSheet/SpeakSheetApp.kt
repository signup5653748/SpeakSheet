package com.SpeakSheet

import android.app.Application
import android.util.Log

class SpeakSheetApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Configure StAX XML parser properties for Apache POI on Android early in application lifecycle
        try {
            System.setProperty("org.apache.poi.javax.xml.stream.XMLInputFactory", "com.fasterxml.aalto.stax.InputFactoryImpl")
            System.setProperty("org.apache.poi.javax.xml.stream.XMLOutputFactory", "com.fasterxml.aalto.stax.OutputFactoryImpl")
            System.setProperty("org.apache.poi.javax.xml.stream.XMLEventFactory", "com.fasterxml.aalto.stax.EventFactoryImpl")
        } catch (e: Throwable) {
            Log.e("SpeakSheetApp", "Failed to set StAX system properties", e)
        }
    }
}
