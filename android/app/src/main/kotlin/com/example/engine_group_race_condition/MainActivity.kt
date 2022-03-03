package com.example.engine_group_race_condition

import android.content.Context
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.FlutterEngineGroup

var engineGroup: FlutterEngineGroup? = null

class MainActivity: FlutterActivity() {
    override fun shouldDestroyEngineWithHost(): Boolean {
        return true;
    }

    override fun provideFlutterEngine(context: Context): FlutterEngine {
        if (engineGroup == null) {
            engineGroup = FlutterEngineGroup(this)
        }

        val engine = engineGroup!!.createAndRunDefaultEngine(this)

        // This sleep() ensures that the flutter code started by createAndRunDefaultEngine() wins
        // the race with FlutterActivity's call to either host.providePlatformPlugin() or
        // perhaps host.configureFlutterEngine()
        Thread.sleep(500);

        return engine;
    }
}
