package com.dinklab.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun demoLoginShowsDashboardAndCustomerSearch() {
        compose.waitUntil(60000) { compose.onAllNodesWithText("Minh Anh · Admin").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Minh Anh · Admin").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("DinkLab", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Khách hàng").performClick()
        compose.onNodeWithText("Tìm khách hàng").performTextInput("Nguyễn Minh")
        compose.waitUntil(10000) { compose.onAllNodesWithText("Nguyễn Minh · 1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Nguyễn Minh · 1").assertIsDisplayed().performClick()
        compose.onNodeWithText("Đăng ký học").assertExists()
    }
}