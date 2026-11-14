package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.local.AppDatabase
import com.example.data.local.PreferencesRepository
import com.example.data.repository.BookRepository
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.QuotesScreen
import com.example.ui.screens.ReaderScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.ListayTheme
import com.example.ui.viewmodel.LibraryEvent
import com.example.ui.viewmodel.LibraryViewModel
import com.example.ui.viewmodel.ReaderViewModel

private object Routes {
  const val LIBRARY = "library"
  const val QUOTES = "quotes"
  const val SETTINGS = "settings"
  const val READER = "reader/{bookId}?unit={unit}&para={para}"

  fun readerRoute(bookId: Long, unit: Int = -1, paragraph: Int = -1): String =
    "reader/$bookId?unit=$unit&para=$paragraph"
}

class MainActivity : ComponentActivity() {

  private var volumeKeyHandler: ((Boolean) -> Boolean)? = null
  private var pendingExternalUri by mutableStateOf<Uri?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    extractIncomingUri(intent)?.let { pendingExternalUri = it }

    val database = AppDatabase.getInstance(applicationContext)
    val bookRepository = BookRepository(applicationContext, database)
    val preferencesRepository = PreferencesRepository(applicationContext)

    setContent {
      val libraryViewModel: LibraryViewModel = viewModel(
        factory = LibraryViewModel.Factory(bookRepository, preferencesRepository),
      )
      val preferences by libraryViewModel.preferences.collectAsStateWithLifecycle()

      ListayTheme(dynamicColor = preferences.useDynamicColor) {
        ListayApp(
          libraryViewModel = libraryViewModel,
          bookRepository = bookRepository,
          preferencesRepository = preferencesRepository,
          pendingExternalUri = pendingExternalUri,
          onExternalUriConsumed = { pendingExternalUri = null },
          registerVolumeKeyHandler = { handler -> volumeKeyHandler = handler },
        )
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    extractIncomingUri(intent)?.let { pendingExternalUri = it }
  }

  override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
      val handler = volumeKeyHandler
      if (handler != null) {
        if (event.action == KeyEvent.ACTION_DOWN) {
          if (handler(event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            return true
          }
        } else if (event.action == KeyEvent.ACTION_UP) {
          return true
        }
      }
    }
    return super.dispatchKeyEvent(event)
  }

  private fun extractIncomingUri(intent: Intent?): Uri? {
    if (intent?.action == Intent.ACTION_VIEW) {
      return intent.data
    }
    return null
  }
}

@Composable
private fun ListayApp(
  libraryViewModel: LibraryViewModel,
  bookRepository: BookRepository,
  preferencesRepository: PreferencesRepository,
  pendingExternalUri: Uri?,
  onExternalUriConsumed: () -> Unit,
  registerVolumeKeyHandler: (((Boolean) -> Boolean)?) -> Unit,
) {
  val navController = rememberNavController()
  val snackbarHostState = remember { SnackbarHostState() }
  val libraryState by libraryViewModel.uiState.collectAsStateWithLifecycle()
  val allQuotes by libraryViewModel.allQuotes.collectAsStateWithLifecycle()
  val preferences by libraryViewModel.preferences.collectAsStateWithLifecycle()

  val bookPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument(),
  ) { uri ->
    if (uri != null) {
      libraryViewModel.importBook(uri, openImmediately = true)
    }
  }

  val openPicker: () -> Unit = {
    bookPickerLauncher.launch(
      arrayOf(
        "application/epub+zip",
        "application/pdf",
        "text/plain",
        "application/x-fictionbook+xml",
        "application/fb2+zip",
        "text/xml",
        "application/xml",
        "application/zip",
        "application/octet-stream",
      )
    )
  }

  LaunchedEffect(pendingExternalUri) {
    if (pendingExternalUri != null) {
      libraryViewModel.importBook(pendingExternalUri, openImmediately = true)
      onExternalUriConsumed()
    }
  }

  LaunchedEffect(Unit) {
    libraryViewModel.events.collect { event ->
      when (event) {
        is LibraryEvent.OpenBook -> {
          navController.navigate(Routes.readerRoute(event.bookId))
        }
        is LibraryEvent.ShowMessage -> {
          snackbarHostState.showSnackbar(event.message)
        }
      }
    }
  }

  val backStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = backStackEntry?.destination?.route
  val showMainTabs = currentRoute in setOf(Routes.LIBRARY, Routes.QUOTES, Routes.SETTINGS)

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useNavigationRail = maxWidth >= 600.dp

    val navigateTab: (String) -> Unit = { route ->
      navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) {
          saveState = true
        }
        launchSingleTop = true
        restoreState = true
      }
    }

    Scaffold(
      snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
      bottomBar = {
        if (showMainTabs && !useNavigationRail) {
          NavigationBar {
            NavigationBarItem(
              selected = currentRoute == Routes.LIBRARY,
              onClick = { navigateTab(Routes.LIBRARY) },
              icon = {
                Icon(
                  imageVector = if (currentRoute == Routes.LIBRARY) {
                    Icons.AutoMirrored.Filled.MenuBook
                  } else {
                    Icons.AutoMirrored.Outlined.MenuBook
                  },
                  contentDescription = stringResource(R.string.tab_library),
                )
              },
              label = { Text(stringResource(R.string.tab_library)) },
              modifier = Modifier.testTag("nav_library"),
            )
            NavigationBarItem(
              selected = currentRoute == Routes.QUOTES,
              onClick = { navigateTab(Routes.QUOTES) },
              icon = {
                Icon(
                  imageVector = if (currentRoute == Routes.QUOTES) {
                    Icons.Default.FormatQuote
                  } else {
                    Icons.Outlined.FormatQuote
                  },
                  contentDescription = stringResource(R.string.tab_quotes),
                )
              },
              label = { Text(stringResource(R.string.tab_quotes)) },
              modifier = Modifier.testTag("nav_quotes"),
            )
            NavigationBarItem(
              selected = currentRoute == Routes.SETTINGS,
              onClick = { navigateTab(Routes.SETTINGS) },
              icon = {
                Icon(
                  imageVector = if (currentRoute == Routes.SETTINGS) {
                    Icons.Default.Settings
                  } else {
                    Icons.Outlined.Settings
                  },
                  contentDescription = stringResource(R.string.tab_settings),
                )
              },
              label = { Text(stringResource(R.string.tab_settings)) },
              modifier = Modifier.testTag("nav_settings"),
            )
          }
        }
      },
    ) { innerPadding ->
      Row(modifier = Modifier.fillMaxSize()) {
        if (showMainTabs && useNavigationRail) {
          NavigationRail(modifier = Modifier.fillMaxHeight()) {
            NavigationRailItem(
              selected = currentRoute == Routes.LIBRARY,
              onClick = { navigateTab(Routes.LIBRARY) },
              icon = {
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.MenuBook,
                  contentDescription = stringResource(R.string.tab_library),
                )
              },
              label = { Text(stringResource(R.string.tab_library)) },
            )
            NavigationRailItem(
              selected = currentRoute == Routes.QUOTES,
              onClick = { navigateTab(Routes.QUOTES) },
              icon = {
                Icon(
                  imageVector = Icons.Default.FormatQuote,
                  contentDescription = stringResource(R.string.tab_quotes),
                )
              },
              label = { Text(stringResource(R.string.tab_quotes)) },
            )
            NavigationRailItem(
              selected = currentRoute == Routes.SETTINGS,
              onClick = { navigateTab(Routes.SETTINGS) },
              icon = {
                Icon(
                  imageVector = Icons.Default.Settings,
                  contentDescription = stringResource(R.string.tab_settings),
                )
              },
              label = { Text(stringResource(R.string.tab_settings)) },
            )
          }
        }

        NavHost(
          navController = navController,
          startDestination = Routes.LIBRARY,
          modifier = if (showMainTabs) Modifier.weight(1f).padding(innerPadding) else Modifier.weight(1f),
        ) {
          composable(Routes.LIBRARY) {
            LibraryScreen(
              uiState = libraryState,
              onSearchQueryChanged = libraryViewModel::onSearchQueryChanged,
              onFormatSelected = libraryViewModel::onFormatFilterSelected,
              onImportBookClick = openPicker,
              onOpenBook = { bookId ->
                navController.navigate(Routes.readerRoute(bookId))
              },
              onDeleteBook = libraryViewModel::deleteBook,
            )
          }

          composable(Routes.QUOTES) {
            QuotesScreen(
              quotes = allQuotes,
              onOpenQuoteInBook = { bookId, unitIdx, paraIdx ->
                navController.navigate(Routes.readerRoute(bookId, unitIdx, paraIdx))
              },
              onDeleteQuote = libraryViewModel::deleteQuote,
            )
          }

          composable(Routes.SETTINGS) {
            SettingsScreen(
              preferences = preferences,
              bookCount = libraryState.books.size,
              quoteCount = allQuotes.size,
              onToggleDynamicColor = libraryViewModel::setUseDynamicColor,
              onToggleVolumePaging = libraryViewModel::setVolumeKeyNavigation,
              onToggleKeepScreenOn = libraryViewModel::setKeepScreenOn,
            )
          }

          composable(
            route = Routes.READER,
            arguments = listOf(
              navArgument("bookId") { type = NavType.LongType },
              navArgument("unit") { type = NavType.IntType; defaultValue = -1 },
              navArgument("para") { type = NavType.IntType; defaultValue = -1 },
            ),
          ) { entry ->
            val bookId = entry.arguments?.getLong("bookId") ?: 0L
            val unitArg = entry.arguments?.getInt("unit")?.takeIf { it >= 0 }
            val paraArg = entry.arguments?.getInt("para")?.takeIf { it >= 0 }
            val readerViewModel: ReaderViewModel = viewModel(
              key = "reader_${bookId}_${unitArg ?: -1}_${paraArg ?: -1}",
              factory = ReaderViewModel.Factory(
                bookId = bookId,
                initialUnitOverride = unitArg,
                initialParagraphOverride = paraArg,
                bookRepository = bookRepository,
                preferencesRepository = preferencesRepository,
              ),
            )
            ReaderScreen(
              viewModel = readerViewModel,
              onNavigateBack = { navController.popBackStack() },
              registerVolumeKeyHandler = registerVolumeKeyHandler,
            )
          }
        }
      }
    }
  }
}
