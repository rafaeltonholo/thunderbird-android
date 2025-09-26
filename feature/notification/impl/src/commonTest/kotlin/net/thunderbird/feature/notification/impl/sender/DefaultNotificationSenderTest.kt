package net.thunderbird.feature.notification.impl.sender

import assertk.assertThat
import assertk.assertions.isInstanceOf
import assertk.assertions.prop
import dev.mokkery.matcher.any
import dev.mokkery.spy
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
import kotlin.test.Test
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import net.thunderbird.core.featureflag.FeatureFlagProvider
import net.thunderbird.core.featureflag.FeatureFlagResult
import net.thunderbird.core.logging.testing.TestLogger
import net.thunderbird.core.outcome.Outcome
import net.thunderbird.feature.notification.api.NotificationRegistry
import net.thunderbird.feature.notification.api.NotificationSeverity
import net.thunderbird.feature.notification.api.command.NotificationCommand.Success
import net.thunderbird.feature.notification.api.content.InAppNotification
import net.thunderbird.feature.notification.api.content.Notification
import net.thunderbird.feature.notification.api.content.SystemNotification
import net.thunderbird.feature.notification.api.receiver.NotificationNotifier
import net.thunderbird.feature.notification.impl.command.DisplayInAppNotificationCommand
import net.thunderbird.feature.notification.impl.command.DisplaySystemNotificationCommand
import net.thunderbird.feature.notification.testing.fake.FakeInAppOnlyNotification
import net.thunderbird.feature.notification.testing.fake.FakeNotification
import net.thunderbird.feature.notification.testing.fake.FakeNotificationRegistry
import net.thunderbird.feature.notification.testing.fake.FakeSystemOnlyNotification
import net.thunderbird.feature.notification.testing.fake.receiver.FakeInAppNotificationNotifier
import net.thunderbird.feature.notification.testing.fake.receiver.FakeSystemNotificationNotifier

class DefaultNotificationSenderTest {

    @Test
    fun `send should emit Success and call system notifier for SystemNotification`() = runTest {
        // Arrange
        val registry = FakeNotificationRegistry()
        val systemNotifier = spy(FakeSystemNotificationNotifier())
        val inAppNotifier = spy(FakeInAppNotificationNotifier())
        val testSubject = createTestSubject(
            notificationRegistry = registry,
            systemNotificationNotifier = systemNotifier,
            inAppNotificationNotifier = inAppNotifier,
        )
        val notification: SystemNotification = FakeSystemOnlyNotification()

        // Act
        val outcomes = testSubject.send(notification).toList(mutableListOf())

        // Assert
        assertThat(outcomes.single())
            .isInstanceOf<Outcome.Success<Success<Notification>>>()
            .prop("data") { it.data }
            .prop(Success<Notification>::command)
            .isInstanceOf<DisplaySystemNotificationCommand>()

        verifySuspend(exactly(1)) { systemNotifier.show(id = any(), notification = any()) }
        // Ensure in-app notifier wasn't called
        verifySuspend(exactly(0)) { inAppNotifier.show(id = any(), notification = any()) }
    }

    @Test
    fun `send should emit Successes and call both notifiers when notification qualifies for both`() = runTest {
        // Arrange: Make system command succeed by using a Critical severity (always show)
        val registry = FakeNotificationRegistry()
        val systemNotifier = spy(FakeSystemNotificationNotifier())
        val inAppNotifier = spy(FakeInAppNotificationNotifier())
        val testSubject = createTestSubject(
            notificationRegistry = registry,
            systemNotificationNotifier = systemNotifier,
            inAppNotificationNotifier = inAppNotifier,
        )
        val notification = FakeNotification(
            // Critical ensures DisplaySystemNotificationCommand can execute even if app is foreground
            severity = NotificationSeverity.Critical,
        )

        // Act
        val outcomes = testSubject.send(notification).toList(mutableListOf())

        // Assert: we expect two outcomes in order: system then in-app
        assertThat(outcomes[0])
            .isInstanceOf<Outcome.Success<Success<Notification>>>()
            .prop("data") { it.data }
            .prop(Success<Notification>::command)
            .isInstanceOf<DisplaySystemNotificationCommand>()
        assertThat(outcomes[1])
            .isInstanceOf<Outcome.Success<Success<Notification>>>()
            .prop("data") { it.data }
            .prop(Success<Notification>::command)
            .isInstanceOf<DisplayInAppNotificationCommand>()

        verifySuspend(exactly(1)) { systemNotifier.show(id = any(), notification) }
        verifySuspend(exactly(1)) { inAppNotifier.show(id = any(), notification) }
    }

    @Test
    fun `send should emit Success and call in-app notifier for InAppNotification`() = runTest {
        // Arrange
        val registry = FakeNotificationRegistry()
        val systemNotifier = spy(FakeSystemNotificationNotifier())
        val inAppNotifier = spy(FakeInAppNotificationNotifier())
        val testSubject = createTestSubject(
            notificationRegistry = registry,
            systemNotificationNotifier = systemNotifier,
            inAppNotificationNotifier = inAppNotifier,
        )
        val notification: InAppNotification = FakeInAppOnlyNotification()

        // Act
        val outcomes = testSubject.send(notification).toList(mutableListOf())

        // Assert
        assertThat(outcomes.single())
            .isInstanceOf<Outcome.Success<Success<Notification>>>()
            .prop("data") { it.data }
            .prop(Success<Notification>::command)
            .isInstanceOf<DisplayInAppNotificationCommand>()

        verifySuspend(exactly(1)) { inAppNotifier.show(id = any(), notification = any()) }
        verifySuspend(exactly(0)) { systemNotifier.show(id = any(), notification = any()) }
    }

    private fun createTestSubject(
        logger: TestLogger = TestLogger(),
        featureFlagProvider: FeatureFlagProvider = FeatureFlagProvider { FeatureFlagResult.Enabled },
        notificationRegistry: NotificationRegistry = FakeNotificationRegistry(),
        systemNotificationNotifier: NotificationNotifier<SystemNotification> = FakeSystemNotificationNotifier(),
        inAppNotificationNotifier: NotificationNotifier<InAppNotification> = FakeInAppNotificationNotifier(),
    ): DefaultNotificationSender {
        return DefaultNotificationSender(
            logger = logger,
            featureFlagProvider = featureFlagProvider,
            notificationRegistry = notificationRegistry,
            systemNotificationNotifier = systemNotificationNotifier,
            inAppNotificationNotifier = inAppNotificationNotifier,
        )
    }
}
