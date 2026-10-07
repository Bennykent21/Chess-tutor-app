package com.example

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chesstutor.app.di.AppContainer
import com.chesstutor.app.ui.navigation.AppNavHost
import com.chesstutor.app.ui.theme.ChessTutorTheme
import com.chesstutor.app.viewmodel.AppViewModel
import com.chesstutor.app.viewmodel.AppViewModelFactory

class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
    )

    setContent {
      val context = LocalContext.current.applicationContext
      val factory = remember(context) {
        val repo = AppContainer.provideReviewRepository(context)
        val engine = AppContainer.provideEngineClient(context)
        val ratingRepo = AppContainer.provideRatingRepository(context)
        val learningRepo = AppContainer.provideLearningRepository(context)
        val engineManager = AppContainer.provideChessEngineManager(context)
        val gameRepo = AppContainer.provideGameRepository(context)
        val soundManager = AppContainer.provideSoundManager(context)
        AppViewModelFactory(repo, engine, ratingRepo, learningRepo, engineManager, gameRepo, soundManager)
      }
      val appViewModel: AppViewModel = viewModel(factory = factory)

      ChessTutorTheme {
        AppNavHost(viewModel = appViewModel)
      }
    }
  }
}

