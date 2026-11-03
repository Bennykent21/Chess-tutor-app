package com.chesstutor.app.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
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
import com.chesstutor.app.navigation.AppRoutes
import com.chesstutor.app.navigation.TopTab

/** The Learn tab: the hub, categories, courses, the lesson player and the opening trainer. */
internal fun NavGraphBuilder.learnGraph(shell: Shell) = with(shell) {
    navigation(startDestination = TopTab.LEARN.startRoute, route = TopTab.LEARN.graphRoute) {
        composable(TopTab.LEARN.startRoute) { entry ->
            val learn = learnViewModel(navController, entry)
            val lessons by learn.learn.collectAsStateWithLifecycle()
            val trainer by trainerViewModel(navController, entry).trainer.collectAsStateWithLifecycle()
            LearnScreen(
                learn = lessons,
                trainerDue = trainer.dueTotal,
                actions = LearnHubActions(
                    onOpenCategory = { category -> navController.navigate(AppRoutes.learnCategory(category.key)) { launchSingleTop = true } },
                    onOpenLesson = { id ->
                        learn.openLesson(id)
                        navController.navigate(AppRoutes.LESSON) { launchSingleTop = true }
                    },
                    onOpenTrainer = { navController.navigate(AppRoutes.TRAINER) { launchSingleTop = true } },
                )
            )
        }
        composable(AppRoutes.TRAINER) { entry ->
            val model = trainerViewModel(navController, entry)
            val trainer by model.trainer.collectAsStateWithLifecycle()
            OpeningTrainerScreen(
                state = trainer,
                onOpen = { id ->
                    model.start(id)
                    navController.navigate(AppRoutes.TRAINER_DRILL) { launchSingleTop = true }
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(AppRoutes.TRAINER_DRILL) { entry ->
            val model = trainerViewModel(navController, entry)
            val drill by model.drill.collectAsStateWithLifecycle()
            val lessons by learnViewModel(navController, entry).learn.collectAsStateWithLifecycle()
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
            val learn = learnViewModel(navController, entry)
            val lessons by learn.learn.collectAsStateWithLifecycle()
            val category = LessonCategory.fromKey(entry.arguments?.getString(AppRoutes.CATEGORY_ARG).orEmpty())
            if (category == null) {
                LaunchedEffect(Unit) { navController.popBackStack() }
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
            route = AppRoutes.COURSE_PATTERN,
            arguments = listOf(navArgument(AppRoutes.COURSE_ID_ARG) { type = NavType.StringType })
        ) { entry ->
            val learn = learnViewModel(navController, entry)
            val homeModel = homeViewModel(navController, entry)
            val lessons by learn.learn.collectAsStateWithLifecycle()
            val courseId = entry.arguments?.getString(AppRoutes.COURSE_ID_ARG).orEmpty()
            val course = lessons.library.course(courseId)
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
            )
        }
        // Home's Continue card: opens one lesson, then shows the player.
        composable(
            route = AppRoutes.LESSON_OPEN_PATTERN,
            arguments = listOf(navArgument(AppRoutes.LESSON_ID_ARG) { type = NavType.StringType })
        ) { entry ->
            val learn = learnViewModel(navController, entry)
            val lessonId = entry.arguments?.getString(AppRoutes.LESSON_ID_ARG).orEmpty()
            LaunchedEffect(lessonId) {
                learn.openLesson(lessonId)
                navController.navigate(AppRoutes.LESSON) {
                    popUpTo(AppRoutes.LESSON_OPEN_PATTERN) { inclusive = true }
                }
            }
        }
        composable(AppRoutes.LESSON) { entry ->
            val learn = learnViewModel(navController, entry)
            val homeModel = homeViewModel(navController, entry)
            val player by learn.player.collectAsStateWithLifecycle()
            val course = player?.course
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
                onRelatedPuzzles = course?.let { navController.relatedPuzzles(it) }
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

/** The Learn tab's view model, shared by the hub, its pages and the lesson player (scoped to its graph). */
@Composable
private fun learnViewModel(navController: NavHostController, entry: NavBackStackEntry): LearnViewModel {
    val owner = remember(entry) { navController.getBackStackEntry(TopTab.LEARN.graphRoute) }
    val context = LocalContext.current.applicationContext
    return viewModel(
        viewModelStoreOwner = owner,
        factory = LearnViewModel.Factory(
            progress = AppContainer.provideLessonProgress(context),
            loadLibrary = { AppContainer.provideLessonLoader(context).load() },
            practice = AppContainer.provideTraining(context),
        ),
    )
}

/** The opening trainer's view model, shared by the Learn hub, the trainer list and the drill (scoped to the Learn graph). */
@Composable
private fun trainerViewModel(navController: NavHostController, entry: NavBackStackEntry): OpeningTrainerViewModel {
    val owner = remember(entry) { navController.getBackStackEntry(TopTab.LEARN.graphRoute) }
    val context = LocalContext.current.applicationContext
    return viewModel(
        viewModelStoreOwner = owner,
        factory = OpeningTrainerViewModel.Factory(AppContainer.provideTraining(context)) { AppContainer.provideOpeningLoader(context).load() },
    )
}
