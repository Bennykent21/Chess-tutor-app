package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.chesstutor.app.di.AppContainer
import com.chesstutor.app.ui.navigation.AppNavHost
import com.chesstutor.app.ui.theme.ChessTutorTheme
import com.chesstutor.app.viewmodel.AppViewModel

class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    setContent {
      val context = LocalContext.current
      val viewModel = remember {
        val repo = AppContainer.provideReviewRepository(context)
        val engine = AppContainer.provideEngineClient(context)
        AppViewModel(repo, engine)
      }

      ChessTutorTheme {
        AppNavHost(viewModel = viewModel)
      }
    }
  }
}

