package com.gv.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import kotlin.test.Test

class MainActivityTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mainScreen_showsHabitsTitle() {
        composeTestRule
            .onNodeWithText("Habits")
            .assertIsDisplayed()
    }
}
