package com.chesstutor.app.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import com.chesstutor.app.di.AppContainer
import com.chesstutor.app.domain.learn.Course
import com.chesstutor.app.domain.learn.LessonCategory
import com.chesstutor.app.domain.learn.OpeningCourses
import com.chesstutor.app.domain.learn.PracticeSets
import com.chesstutor.app.feature.home.HomeViewModel
import com.chesstutor.app.feature.learn.CourseScreen
import com.chesstutor.app.feature.learn.LearnCategoryScreen
import com.chesstutor.app.feature.learn.LearnHubActions
import com.chesstutor.app.feature.learn.LearnScreen
import com.chesstutor.app.feature.learn.LearnViewModel
import com.chesstutor.app.feature.learn.LessonPlayerScreen
import com.chesstutor.app.feature.learn.OpeningDrillScreen
import com.chesstutor.app.feature.learn.OpeningTrainerScreen
import com.chesstutor.app.feature.learn.OpeningTrainerViewModel
import com.chesstutor.app.feature.learn.RepertoireActions
import com.chesstutor.app.feature.learn.RepertoirePage
import com.chesstutor.app.feature.learn.RepertoireRunScreen
import com.chesstutor.app.feature.learn.RepertoireViewModel
import com.chesstutor.app.feature.learn.OpeningsScreen
import com.chesstutor.app.feature.learn.OpeningGroup
import com.chesstutor.app.feature.learn.LearnOpeningScreen
import com.chesstutor.app.feature.learn.CourseDrills
import com.chesstutor.app.navigation.AppRoutes
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.combine
import com.chesstutor.app.domain.learn.LessonLibrary
import com.chesstutor.app.domain.learn.CourseGate
import com.chesstutor.app.navigation.TopTab

/** The Learn tab: the hub, categories, courses, the lesson player and the opening trainer. */
internal fun NavGraphBuilder.learnGraph(shell: Shell) = with(shell) {
    navigation(startDestination = TopTab.LEARN.startRoute, route = TopTab.LEARN.graphRoute) {
        composable(TopTab.LEARN.startRoute) { entry ->
            val learn = learnViewModel()
            val lessons by learn.learn.collectAsStateWithLifecycle()
            LearnScreen(
                learn = lessons,
                actions = LearnHubActions(
                    onOpenCategory = { category -> navController.navigate(AppRoutes.learnCategory(category.key)) { launchSingleTop = true } },
                    onOpenLesson = { id ->
                        learn.openLesson(id)
                        navController.navigate(AppRoutes.LESSON) { launchSingleTop = true }
                    },
                )
            )
        }
        composable(AppRoutes.TRAINER) { entry ->
            val model = trainerViewModel()
            val trainer by model.trainer.collectAsStateWithLifecycle()
            val repertoire = repertoireViewModel()
            val repertoires by repertoire.state.collectAsStateWithLifecycle()
            OpeningTrainerScreen(
                state = trainer,
                onOpen = { id ->
                    model.start(id)
                    navController.navigate(AppRoutes.TRAINER_DRILL) { launchSingleTop = true }
                },
                onBack = { navController.popBackStack() },
                repertoires = repertoires.books,
                onOpenRepertoire = { id -> navController.navigate(AppRoutes.repertoire(id)) { launchSingleTop = true } },
                onReviewRepertoires = {
                    repertoire.startReview()
                    navController.navigate(AppRoutes.REPERTOIRE_RUN) { launchSingleTop = true }
                },
            )
        }
        composable(
            route = AppRoutes.REPERTOIRE_PATTERN,
            arguments = listOf(navArgument(AppRoutes.REPERTOIRE_ID_ARG) { type = NavType.StringType })
        ) { entry ->
            val repertoire = repertoireViewModel()
            val repertoires by repertoire.state.collectAsStateWithLifecycle()
            val id = entry.arguments?.getString(AppRoutes.REPERTOIRE_ID_ARG).orEmpty()
            val ui = repertoires.book(id)
            if (ui == null) {
                if (!repertoires.loading) LaunchedEffect(Unit) { navController.popBackStack() }
                return@composable
            }
            val homeModel = homeViewModel()
            val lessons by learnViewModel().learn.collectAsStateWithLifecycle()
            val ideas = OpeningCourses.courseFor(ui.book.title, lessons.library.courses)
            val openRun: (() -> Unit) -> () -> Unit = { start ->
                {
                    start()
                    navController.navigate(AppRoutes.REPERTOIRE_RUN) { launchSingleTop = true }
                }
            }
            RepertoirePage(
                ui = ui,
                actions = RepertoireActions(
                    onBack = { navController.popBackStack() },
                    onReview = openRun { repertoire.startReview(id) },
                    onLearn = openRun { repertoire.startLearn(id) },
                    onLearnVariation = { path -> openRun { repertoire.startLearn(id, path) }() },
                    onIdeas = ideas?.let { course -> { navController.navigate(AppRoutes.course(course.id)) { launchSingleTop = true } } },
                    onTricky = openRun { repertoire.startTricky(id) },
                    onCourse = lessons.library.courses.firstOrNull { it.opening == id }?.let {
                        { navController.navigate(AppRoutes.learnOpening(id)) { launchSingleTop = true } }
                    },
                    onTest = openRun { repertoire.startTest(id) },
                    onPlayOut = {
                        homeModel.presetBattle(ui.battleLine, ui.book.side)
                        navController.navigateToTab(TopTab.HOME)
                        navController.navigate(AppRoutes.BATTLE) { launchSingleTop = true }
                    },
                ),
            )
        }
        composable(AppRoutes.REPERTOIRE_RUN) {
            val repertoire = repertoireViewModel()
            val run by repertoire.run.collectAsStateWithLifecycle()
            val leave = {
                repertoire.close()
                navController.popBackStack()
            }
            BackHandler { leave() }
            RepertoireRunScreen(
                run = run,
                settings = state.appSettings,
                onFeedback = viewModel::playBoardFeedback,
                onHint = repertoire::hint,
                onWhy = repertoire::why,
                onWatchAgain = repertoire::watchAgain,
                onSkipWatch = repertoire::skipWatch,
                onLearnMore = repertoire::learnMore,
                onDismissMastered = repertoire::dismissMastered,
                onBack = { leave() },
            )
        }
        composable(AppRoutes.TRAINER_DRILL) { entry ->
            val model = trainerViewModel()
            val drill by model.drill.collectAsStateWithLifecycle()
            val lessons by learnViewModel().learn.collectAsStateWithLifecycle()
            val courseId = drill?.let { OpeningCourses.courseFor(it.opening.family, lessons.library.courses)?.id }
            val leave = {
                model.close()
                navController.popBackStack()
            }
            BackHandler { leave() }
            OpeningDrillScreen(
                drill = drill,
                settings = state.appSettings,
                onFeedback = viewModel::playBoardFeedback,
                onShowAnswer = model::showAnswer,
                onAgain = model::again,
                onNext = model::next,
                onPickLine = { lineId -> drill?.let { model.startLine(it.opening.id, lineId) } },
                onBack = { leave() },
                onLearn = courseId?.let { id -> { navController.navigate(AppRoutes.course(id)) { launchSingleTop = true } } },
            )
        }
        composable(
            route = AppRoutes.LEARN_CATEGORY_PATTERN,
            arguments = listOf(navArgument(AppRoutes.CATEGORY_ARG) { type = NavType.StringType })
        ) { entry ->
            val learn = learnViewModel()
            val lessons by learn.learn.collectAsStateWithLifecycle()
            val category = LessonCategory.fromKey(entry.arguments?.getString(AppRoutes.CATEGORY_ARG).orEmpty())
            if (category == null) {
                LaunchedEffect(Unit) { navController.popBackStack() }
                return@composable
            }
            if (category == LessonCategory.OPENINGS) {
                val repertoires by repertoireViewModel().state.collectAsStateWithLifecycle()
                OpeningsScreen(
                    groups = OpeningGroup.of(lessons.library.coursesIn(category), repertoires.books),
                    progress = lessons.progress,
                    theme = state.appSettings.boardTheme,
                    onOpen = { group ->
                        val route = if (group.courses.size == 1) AppRoutes.course(group.courses.single().id) else AppRoutes.learnOpening(group.id)
                        navController.navigate(route) { launchSingleTop = true }
                    },
                    onBack = { navController.popBackStack() },
                )
                return@composable
            }
            LearnCategoryScreen(
                category = category,
                learn = lessons,
                onOpenCourse = { id -> navController.navigate(AppRoutes.course(id)) { launchSingleTop = true } },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = AppRoutes.LEARN_OPENING_PATTERN,
            arguments = listOf(navArgument(AppRoutes.OPENING_ID_ARG) { type = NavType.StringType })
        ) { entry ->
            val lessons by learnViewModel().learn.collectAsStateWithLifecycle()
            val repertoire = repertoireViewModel()
            val repertoires by repertoire.state.collectAsStateWithLifecycle()
            val id = entry.arguments?.getString(AppRoutes.OPENING_ID_ARG).orEmpty()
            val group = OpeningGroup.of(lessons.library.coursesIn(LessonCategory.OPENINGS), repertoires.books).firstOrNull { it.id == id }
            if (group == null) {
                if (!lessons.loading && !repertoires.loading) LaunchedEffect(Unit) { navController.popBackStack() }
                return@composable
            }
            LearnOpeningScreen(
                group = group,
                progress = lessons.progress,
                onOpenCourse = { course -> navController.navigate(AppRoutes.course(course.id)) { launchSingleTop = true } },
                onTest = group.book?.let { book ->
                    {
                        repertoire.startTest(book.book.id)
                        navController.navigate(AppRoutes.REPERTOIRE_RUN) { launchSingleTop = true }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = AppRoutes.COURSE_PATTERN,
            arguments = listOf(navArgument(AppRoutes.COURSE_ID_ARG) { type = NavType.StringType })
        ) { entry ->
            val learn = learnViewModel()
            val homeModel = homeViewModel()
            val lessons by learn.learn.collectAsStateWithLifecycle()
            val courseId = entry.arguments?.getString(AppRoutes.COURSE_ID_ARG).orEmpty()
            val course = lessons.library.course(courseId)
            val repertoires by repertoireViewModel().state.collectAsStateWithLifecycle()
            // The trainer's opening this course belongs to (Phase 12).
            val book = course?.opening?.let { repertoires.book(it) }
            CourseScreen(
                course = course,
                progress = lessons.progress,
                onOpenLesson = { id ->
                    learn.openLesson(id)
                    navController.navigate(AppRoutes.LESSON) { launchSingleTop = true }
                },
                onBack = { navController.popBackStack() },
                onPractise = course?.let { navController.practiseInBattle(it, homeModel) },
                onRelatedPuzzles = course?.let { navController.relatedPuzzles(it) },
                onPracticeSet = course?.takeIf(PracticeSets::offers)?.let { c ->
                    {
                        navController.navigateToTab(TopTab.PUZZLES)
                        navController.navigate(AppRoutes.puzzlePractice(c.id)) { launchSingleTop = true }
                    }
                },
                practice = course?.let { lessons.practice[it.id] },
                drills = book?.let { b ->
                    val open = !b.locked && course.variations.all { CourseGate.isOpen(it, b.open) }
                    CourseDrills(open = open, onOpen = { navController.navigate(AppRoutes.repertoire(b.book.id)) { launchSingleTop = true } })
                },
            )
        }
        // Home's Continue card: opens one lesson, then shows the player.
        composable(
            route = AppRoutes.LESSON_OPEN_PATTERN,
            arguments = listOf(navArgument(AppRoutes.LESSON_ID_ARG) { type = NavType.StringType })
        ) { entry ->
            val learn = learnViewModel()
            val lessonId = entry.arguments?.getString(AppRoutes.LESSON_ID_ARG).orEmpty()
            LaunchedEffect(lessonId) {
                learn.openLesson(lessonId)
                navController.navigate(AppRoutes.LESSON) {
                    popUpTo(AppRoutes.LESSON_OPEN_PATTERN) { inclusive = true }
                }
            }
        }
        composable(AppRoutes.LESSON) { entry ->
            val learn = learnViewModel()
            val homeModel = homeViewModel()
            val player by learn.player.collectAsStateWithLifecycle()
            val lessons by learn.learn.collectAsStateWithLifecycle()
            val course = player?.course
            // A finished variation course opens its drills: the last page offers them.
            val trainable = course?.takeIf { it.opening != null && CourseGate.isDone(it, lessons.progress) }
            LessonPlayerScreen(
                player = player,
                settings = state.appSettings,
                onFeedback = viewModel::playBoardFeedback,
                onNextLesson = { id -> learn.openLesson(id) },
                onPreviousLesson = { id -> learn.openLesson(id) },
                onReplay = { player?.lesson?.id?.let { learn.openLesson(it, fromStart = true) } },
                onBack = {
                    learn.closePlayer()
                    navController.popBackStack()
                },
                onPractise = course?.let { navController.practiseInBattle(it, homeModel) },
                onRelatedPuzzles = course?.let { navController.relatedPuzzles(it) },
                onTrain = trainable?.opening?.let { id ->
                    {
                        learn.closePlayer()
                        navController.popBackStack()
                        navController.navigate(AppRoutes.repertoire(id)) { launchSingleTop = true }
                    }
                },
            )
        }
    }
}

/** "Practise in Opening Battle" for [course]: presets Home's battle page and opens it. Null without a line. */
private fun NavHostController.practiseInBattle(course: Course, home: HomeViewModel): (() -> Unit)? {
    val line = course.battleLine ?: return null
    return {
        home.presetBattle(line, course.side)
        navigateToTab(TopTab.HOME)
        navigate(AppRoutes.BATTLE) { launchSingleTop = true }
    }
}

/** "Related puzzles" for [course]: puzzles with its themes in the Puzzles tab. Null without themes. */
private fun NavHostController.relatedPuzzles(course: Course): (() -> Unit)? {
    if (course.puzzleThemes.isEmpty()) return null
    return {
        navigateToTab(TopTab.PUZZLES)
        navigate(AppRoutes.puzzleRelated(course.puzzleThemes)) { launchSingleTop = true }
    }
}

/** The Learn tab's view model, shared by the hub, its pages and the lesson player (it lives as long as the app). */
@Composable
internal fun Shell.learnViewModel(): LearnViewModel {
    val context = LocalContext.current.applicationContext
    return viewModel(
        viewModelStoreOwner = appOwner,
        factory = LearnViewModel.Factory(
            progress = AppContainer.provideLessonProgress(context),
            loadLibrary = { AppContainer.provideLessonLoader(context).load() },
            practice = AppContainer.provideTraining(context),
            evaluate = { fen ->
                AppContainer.provideEngineService(context).evaluate(fen, LESSON_EVAL_MS)?.let { it.centipawns to it.mateInMoves }
            },
        ),
    )
}

/** Thinking time for a lesson position's evaluation: enough for a steady bar, short enough to keep pace. */
private const val LESSON_EVAL_MS = 300

/** The taught openings (Phase 10), shared by the hub, the trainer list, the opening page and its runs (it lives as long as the app). */
@Composable
internal fun Shell.repertoireViewModel(): RepertoireViewModel {
    val context = LocalContext.current.applicationContext
    val app = viewModel
    return viewModel(
        viewModelStoreOwner = appOwner,
        factory = RepertoireViewModel.Factory(
            repository = AppContainer.provideRepertoireProgress(context),
            loadBooks = { AppContainer.provideRepertoireLoader(context).load() },
            rating = { app.state.value.effectiveUserRating },
            gate = combine(
                flow { emit(runCatching { AppContainer.provideLessonLoader(context).load() }.getOrDefault(LessonLibrary.EMPTY)) },
                AppContainer.provideLessonProgress(context).observe(),
                AppContainer.provideOpeningTests(context).passed(),
            ) { library, progress, passed -> CourseGate.opened(library, progress, passed) },
            tests = AppContainer.provideOpeningTests(context),
        ),
    )
}

/** The opening trainer's view model, shared by the Learn hub, the trainer list and the drill (it lives as long as the app). */
@Composable
internal fun Shell.trainerViewModel(): OpeningTrainerViewModel {
    val context = LocalContext.current.applicationContext
    return viewModel(
        viewModelStoreOwner = appOwner,
        factory = OpeningTrainerViewModel.Factory(AppContainer.provideTraining(context)) { AppContainer.provideOpeningLoader(context).load() },
    )
}
