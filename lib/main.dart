import 'dart:io';

import 'package:flutter/material.dart';

void main() {
  print("Platform is Android: ${Platform.isAndroid}");
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
