# engine_group_race_condition

This Flutter project demonstrates a race condition between FlutterEngineGroup and FlutterActivity

## Steps to reproduce

1. Execute `flutter run --release` on the code sample

**Expected results:** The app displays a centered Text widget with "Locale: en_US"

**Actual results:** The app displays a centered Text widget with "Locale: und"

The issue is a race condition that occurs when using `FlutterEngineGroup` to provide a `FlutterEngine` to a `FlutterActivity`. 

**RootCause**

The root cause of issue seems to be that `FlutterEngineGroup` requires an entrypoint to be specified when the engine is created and it executes the entrypoint immediately. As a result, the engine returned by `FlutterActivity.provideFlutterEngine()` is already running. However, the `FlutterActivityAndFragmentDelegate.onAttach`, which indirectly called `provideFutterEngine()`, then sets up the `PlatformPlugin` using `host.providePlatformPlugin()` and calls `host.configureFlutterEngine()`.

Since the engine is already running when it's returned from `FlutterActivity.provideFlutterEngine()`, there is a race between when the flutter code tries to access `Platform.localeName` and when the native code calls `host.providePlatformPlugin()`

**Code Sample**

A flutter project that illustrates the issue can be found at: https://github.com/champeauxr/engine_group_race_condition

The Flutter UI just centers a `Text` widget that displays the current locale name:
```
import 'dart:io';
import 'package:flutter/material.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({Key? key}) : super(key: key);

  // This widget is the root of your application.
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        body: Center(
          child: Text('Locale: ${Platform.localeName}'),
        ),
      ),
    );
  }
}
```

The native Android code simply overrides the `provideFlutterEngin()` and `shouldDestroyEngineWithHost()` methods of `FlutterActivity` to provide an engine created using the `FlutterEngineGroup`.
```
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
```

A sleep() was added after creating the engine to ensure that the Flutter code wins the race. Without this sleep(), the problem is intermittent and happens more often than not on a release build, and with much less frequency on a debug build. Also, when running a debug build, the `Text` widget displays "Locale: und" for a fraction of a second before refreshing and displaying "Locale: en_US".

**Additional Investigation Notes**

`FlutterActivity.onCreate()` calls `FlutterActivityAndFragment.onAttach()`
```
  void onAttach(@NonNull Context context) {
    ensureAlive();

    // When "retain instance" is true, the FlutterEngine will survive configuration
    // changes. Therefore, we create a new one only if one does not already exist.
    if (flutterEngine == null) {
      setupFlutterEngine();
    }

    if (host.shouldAttachEngineToActivity()) {
      // Notify any plugins that are currently attached to our FlutterEngine that they
      // are now attached to an Activity.
      //
      // Passing this Fragment's Lifecycle should be sufficient because as long as this Fragment
      // is attached to its Activity, the lifecycles should be in sync. Once this Fragment is
      // detached from its Activity, that Activity will be detached from the FlutterEngine, too,
      // which means there shouldn't be any possibility for the Fragment Lifecycle to get out of
      // sync with the Activity. We use the Fragment's Lifecycle because it is possible that the
      // attached Activity is not a LifecycleOwner.
      Log.v(TAG, "Attaching FlutterEngine to the Activity that owns this delegate.");
      flutterEngine.getActivityControlSurface().attachToActivity(this, host.getLifecycle());
    }

    // Regardless of whether or not a FlutterEngine already existed, the PlatformPlugin
    // is bound to a specific Activity. Therefore, it needs to be created and configured
    // every time this Fragment attaches to a new Activity.
    // TODO(mattcarroll): the PlatformPlugin needs to be reimagined because it implicitly takes
    //                    control of the entire window. This is unacceptable for non-fullscreen
    //                    use-cases.
    platformPlugin = host.providePlatformPlugin(host.getActivity(), flutterEngine);

    host.configureFlutterEngine(flutterEngine);
    isAttached = true;
  }
```
The call to `setupFlutterEngine()` is what calls `FlutterActivity.provideFlutterEngine()` and when `setupFlutterEngine()` returns, the engine is already running the Flutter code.

At the end of `onAttach()`, it runs the following two lines:
```
    platformPlugin = host.providePlatformPlugin(host.getActivity(), flutterEngine);

    host.configureFlutterEngine(flutterEngine);
```

Presumably, one of these two lines connects up the `PlatformPlugin` to the Flutter engine and prior to these being called, the Flutter code would not be able to retrieve the locale name from `Platform.localeName`. However the FlutterEngine produced by the `FlutterEngineGroup` is already running Flutter code before we get here and may have already attempted to retrieve the locale name.

In normal scenarios, where the `FlutterEngineGroup` is not used, the entrypoint on the Flutter engine isn't executed until `FlutterActivityAndFragmentDelegate.onStart()` is called by `FlutterActivity.onStart()`
```
  void onStart() {
    Log.v(TAG, "onStart()");
    ensureAlive();
    doInitialFlutterViewRun();
  }
```

It would seem that `FlutterEngineGroup` should provide a method that creates an engine without executing an entrypoint. This would allow other initialization, such as that performed by `FlutterActivityAndFragmentDelegate` to be performed before the entrypoint is executed.
