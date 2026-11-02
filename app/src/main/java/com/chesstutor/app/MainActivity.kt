package com.chesstutor.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chesstutor.app.data.diagnostics.CrashLog
import com.chesstutor.app.data.network.WorkImportRunner
import com.chesstutor.app.di.AppContainer
import com.chesstutor.app.ui.navigation.AppNavHost
import com.chesstutor.app.viewmodel.AppViewModel
import com.chesstutor.app.viewmodel.AppViewModelFactory

class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    CrashLog.install(applicationContext, BuildConfig.VERSION_NAME)
    super.onCreate(savedInstanceState)
    // Bar icons start from the phone's light or dark setting; CtTheme then matches the app's mode.
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
    )

    setContent {
      val context = LocalContext.current.applicationContext
      val factory = remember(context) {
        AppViewModelFactory(
          reviewRepository = AppContainer.provideReviewRepository(context),
          engineManager = AppContainer.provideChessEngineManager(context),
          ratingRepository = AppContainer.provideRatingRepository(context),
          learningRepository = AppContainer.provideLearningRepository(context),
          gameRepository = AppContainer.provideGameRepository(context),
          soundManager = AppContainer.provideSoundManager(context),
          settingsStore = AppContainer.provideSettingsStore(context),
          // Long imports run in WorkManager, so they carry on when the app leaves the screen.
          importer = { scope -> WorkImportRunner(context, scope) },
        )
      }
      val appViewModel: AppViewModel = viewModel(factory = factory)

      AppNavHost(viewModel = appViewModel)
    }
  }
}

