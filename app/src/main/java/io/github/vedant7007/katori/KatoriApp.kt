package io.github.vedant7007.katori

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.github.vedant7007.katori.data.local.ProfileStore
import io.github.vedant7007.katori.ml.asr.SpeechLanguage
import io.github.vedant7007.katori.orchestration.WarmUp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Application entry point.
 *
 * Nothing heavy happens on the main thread here: the one thing it starts is the 0032 warm-up,
 * which reads the speech language off the profile and loads and touches the three models on a
 * background dispatcher. Work in Application.onCreate on the main thread before the first frame
 * is how an app that loads a multi-gigabyte model ends up with an ANR at launch (spec 9.3).
 */
@HiltAndroidApp
class KatoriApp : Application() {

    @Inject lateinit var warmUp: WarmUp
    @Inject lateinit var profile: ProfileStore

    override fun onCreate() {
        super.onCreate()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            val tag = profile.speechLanguage.first()
            warmUp.start(SpeechLanguage.entries.firstOrNull { it.tag == tag } ?: SpeechLanguage.HINDI)
        }
    }
}
