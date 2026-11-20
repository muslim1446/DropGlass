package com.example

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.model.DropCategory
import com.example.model.DropNotification
import com.example.model.DropProgressInfo
import com.example.system.DropTestActionReceiver
import com.example.system.LiveNotificationStore
import com.example.system.SystemBridgeHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-440dpi")
class DropGlassFeaturesTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        LiveNotificationStore.setOverlayShowing(false)
        LiveNotificationStore.clearActiveSystemDrop()
    }

    @Test
    fun `in-place progress update increments updateCount without losing key`() {
        val initial = DropNotification(
            id = "k1",
            key = "k1",
            packageName = "com.grab.delivery",
            appName = "Grab",
            title = "Delivery · ETA 8 min",
            text = "Picked up",
            category = DropCategory.DELIVERY,
            progressInfo = DropProgressInfo(progress = 40, max = 100, activeMilestoneIndex = 1)
        )
        LiveNotificationStore.publishSystemDrop(initial, null)

        val updated = initial.copy(
            title = "Delivery · ETA 2 min",
            text = "200m away",
            progressInfo = DropProgressInfo(progress = 85, max = 100, activeMilestoneIndex = 2)
        )
        LiveNotificationStore.publishSystemDrop(updated, null)

        val active = LiveNotificationStore.activeSystemDrop.value
        assertNotNull(active)
        assertEquals(85, active?.progressInfo?.progress)
        assertEquals(1, active?.updateCount)
    }

    @Test
    fun `queueing and cycling multiple notifications preserves all drops`() {
        LiveNotificationStore.setOverlayShowing(true)
        val first = DropNotification(
            id = "n1",
            key = "n1",
            packageName = "com.whatsapp",
            appName = "WhatsApp",
            title = "Alex",
            text = "Ping 1"
        )
        val second = DropNotification(
            id = "n2",
            key = "n2",
            packageName = "com.slack",
            appName = "Slack",
            title = "Design",
            text = "Ping 2"
        )

        LiveNotificationStore.publishSystemDrop(first, null)
        LiveNotificationStore.publishSystemDrop(second, null)

        assertEquals("n2", LiveNotificationStore.activeSystemDrop.value?.id)
        assertEquals(1, LiveNotificationStore.queuedDrops.value.size)

        LiveNotificationStore.cycleQueuedDrop()
        assertEquals("n1", LiveNotificationStore.activeSystemDrop.value?.id)

        LiveNotificationStore.clearActiveSystemDrop("n1")
        assertEquals("n2", LiveNotificationStore.activeSystemDrop.value?.id)
    }

    @Test
    fun `test action receiver handles delivery advance and mark as read`() {
        val nm = context.getSystemService(NotificationManager::class.java)!!
        val shadowNm = shadowOf(nm)

        SystemBridgeHelper.postTestDeliveryNotification(context, step = 1)
        assertNotNull(shadowNm.getNotification(DropTestActionReceiver.DEFAULT_DELIVERY_NOTIF_ID))

        val receiver = DropTestActionReceiver()
        receiver.onReceive(
            context,
            Intent(DropTestActionReceiver.ACTION_DELIVERY_ADVANCE).apply {
                putExtra(DropTestActionReceiver.EXTRA_NOTIF_ID, DropTestActionReceiver.DEFAULT_DELIVERY_NOTIF_ID)
                putExtra(DropTestActionReceiver.EXTRA_DELIVERY_STEP, 1)
            }
        )
        val advanced = shadowNm.getNotification(DropTestActionReceiver.DEFAULT_DELIVERY_NOTIF_ID)
        assertNotNull(advanced)
        assertEquals("2 min", advanced.extras.getString(SystemBridgeHelper.EXTRA_DELIVERY_ETA))

        receiver.onReceive(
            context,
            Intent(DropTestActionReceiver.ACTION_MARK_READ).apply {
                putExtra(DropTestActionReceiver.EXTRA_NOTIF_ID, DropTestActionReceiver.DEFAULT_DELIVERY_NOTIF_ID)
            }
        )
        assertNull(shadowNm.getNotification(DropTestActionReceiver.DEFAULT_DELIVERY_NOTIF_ID))
    }

    @Test
    fun `navigation across Stage Optics and Bridge tabs works`() {
        composeRule.onNodeWithTag("stage_lazy_list")
            .performScrollToNode(hasTestTag("scenario_delivery"))
        composeRule.onNodeWithTag("scenario_delivery").assertIsDisplayed().performClick()

        composeRule.onNodeWithTag("nav_optics").performClick()
        composeRule.onNodeWithTag("optics_lazy_list")
            .performScrollToNode(hasTestTag("preset_cph2773_button"))
        composeRule.onNodeWithTag("preset_cph2773_button").assertIsDisplayed().performClick()

        composeRule.onNodeWithTag("nav_bridge").performClick()
        composeRule.onNodeWithTag("bridge_lazy_list")
            .performScrollToNode(hasTestTag("perm_notification_access_row"))
        composeRule.onNodeWithTag("perm_notification_access_row").assertIsDisplayed()
    }
}
