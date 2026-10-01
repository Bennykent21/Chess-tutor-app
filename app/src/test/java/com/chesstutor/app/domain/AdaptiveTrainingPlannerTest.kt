package com.chesstutor.app.domain
import com.chesstutor.app.data.model.LearningProfile
import com.chesstutor.app.data.model.ModuleProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
class AdaptiveTrainingPlannerTest {
 @Test fun tacticsGoalPrioritizesTacticalLesson() { val r=AdaptiveTrainingPlanner.recommend(LearningProfile(estimatedRating=1000,learningGoal="TACTICS"),emptyList()); assertEquals(SkillDomain.TACTICS,r.domain); assertTrue(r.topic.category==LearnCategory.TACTICS||r.topic.category==LearnCategory.BLUNDER_PATTERNS) }
 @Test fun practicedWeakLessonCanBeRecommendedAgain() { val p=listOf(ModuleProgress("tactics_knight_fork",true,false,10,2)); val r=AdaptiveTrainingPlanner.recommend(LearningProfile(estimatedRating=1000,learningGoal="TACTICS"),p); assertEquals("tactics_knight_fork",r.topic.id) }
 @Test fun masteredLessonIsSkippedWhenUnmasteredPreferredLessonExists() {
  val p = listOf(
   ModuleProgress("tactics_knight_fork", true, true, 10, 9)
  )
  val r = AdaptiveTrainingPlanner.recommend(
   LearningProfile(estimatedRating = 1000, learningGoal = "TACTICS"), p
  )
  assertTrue(r.topic.id != "tactics_knight_fork")
 }
 @Test fun weakMasteredLessonDoesNotTriggerRemediation() {
  val p = listOf(
   ModuleProgress("tactics_knight_fork", true, true, 10, 2)
  )
  val r = AdaptiveTrainingPlanner.recommend(
   LearningProfile(estimatedRating = 1000, learningGoal = "TACTICS"), p
  )
  assertTrue(r.topic.id != "tactics_knight_fork")
 }
 @Test fun generalGoalUsesTopicSkillRating() { val r=AdaptiveTrainingPlanner.recommend(LearningProfile(estimatedRating=2000),emptyList()); assertEquals("tactics_smothered_mate", r.topic.id); assertEquals(1600, r.topic.skillRating); assertEquals(2000, r.skillRating) }
}