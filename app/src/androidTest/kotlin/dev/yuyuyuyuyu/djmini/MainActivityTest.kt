package dev.yuyuyuyuyu.djmini

import android.Manifest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule

@OptIn(ExperimentalTestApi::class)
class MainActivityTest {
    private val composeTestRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: TestRule =
        RuleChain
            .outerRule(GrantPermissionRule.grant(Manifest.permission.READ_MEDIA_AUDIO))
            .around(composeTestRule)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun shouldInviteTheUserToAskGeminiWhenGeminiCanUseDjMini() {
        // Act
        composeTestRule.waitUntilAtLeastOneExists(hasText(context.getString(R.string.music_access_granted)), timeoutMillis = 40_000)

        // Assert
        composeTestRule.onNodeWithText(context.getString(R.string.gemini_availability_unavailable)).assertDoesNotExist()
    }
}
