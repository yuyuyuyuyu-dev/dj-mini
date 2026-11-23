package dev.yuyuyuyuyu.djmini.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.yuyuyuyuyu.djmini.R
import org.junit.Rule
import org.junit.Test

class GeminiUnavailableScreenTest {
    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun shouldShowAnErrorInsteadOfTheMusicAccessButtonWhenGeminiCannotUseDjMini() {
        // Arrange
        val activity = composeTestRule.activity

        // Act
        composeTestRule.setContent { MainScreen(listOf("Sample"), GeminiAvailability.Unavailable, MusicAccess.NotGranted, {}) }

        // Assert
        composeTestRule.onNodeWithText(activity.getString(R.string.gemini_availability_unavailable)).assertExists()
        composeTestRule.onNodeWithText(activity.getString(R.string.music_access_request)).assertDoesNotExist()
    }
}
