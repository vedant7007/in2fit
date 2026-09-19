package io.github.vedant7007.katori

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point.
 *
 * Deliberately empty. Nothing heavy happens here: model loading goes through the ModelArbiter on
 * a background dispatcher, and the database opens lazily. Work in Application.onCreate runs on
 * the main thread before the first frame, which is how an app that loads a multi-gigabyte model
 * ends up with an ANR at launch (spec 9.3).
 */
@HiltAndroidApp
class KatoriApp : Application()
