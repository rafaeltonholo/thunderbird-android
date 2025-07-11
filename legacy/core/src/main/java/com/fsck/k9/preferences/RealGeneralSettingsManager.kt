@file:Suppress("DEPRECATION")

package com.fsck.k9.preferences

import app.k9mail.legacy.di.DI
import com.fsck.k9.K9
import com.fsck.k9.Preferences
import com.fsck.k9.QuietTimeChecker
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import net.thunderbird.core.logging.legacy.Log
import net.thunderbird.core.preference.AppTheme
import net.thunderbird.core.preference.BackgroundSync
import net.thunderbird.core.preference.GeneralSettings
import net.thunderbird.core.preference.GeneralSettingsManager
import net.thunderbird.core.preference.PreferenceChangePublisher
import net.thunderbird.core.preference.SubTheme
import net.thunderbird.core.preference.privacy.PrivacySettingsPreferenceManager
import net.thunderbird.core.preference.storage.Storage
import net.thunderbird.core.preference.storage.StorageEditor
import net.thunderbird.core.preference.storage.getEnumOrDefault
import net.thunderbird.core.preference.storage.putEnum

internal const val KEY_SHOULD_SHOW_SETUP_ARCHIVE_FOLDER_DIALOG = "shouldShowSetupArchiveFolderDialog"
internal const val KEY_CHANGE_REGISTERED_NAME_COLOR = "changeRegisteredNameColor"
internal const val KEY_COLORIZE_MISSING_CONTACT_PICTURE = "colorizeMissingContactPictures"
internal const val KEY_USE_BACKGROUND_AS_UNREAD_INDICATOR = "isUseBackgroundAsUnreadIndicator"
internal const val KEY_SHOW_COMPOSE_BUTTON_ON_MESSAGE_LIST = "showComposeButtonOnMessageList"
internal const val KEY_THREAD_VIEW_ENABLED = "isThreadedViewEnabled"
internal const val KEY_MESSAGE_VIEW_FIXED_WIDTH_FONT = "messageViewFixedWidthFont"
internal const val KEY_AUTO_FIT_WIDTH = "autofitWidth"
internal const val KEY_QUIET_TIME_ENDS = "quietTimeEnds"
internal const val KEY_QUIET_TIME_STARTS = "quietTimeStarts"
internal const val KEY_QUIET_TIME_ENABLED = "quietTimeEnabled"

/**
 * Retrieve and modify general settings.
 *
 * Currently general settings are split between [K9] and [GeneralSettings]. The goal is to move everything over to
 * [GeneralSettings] and get rid of [K9].
 *
 * The [GeneralSettings] instance managed by this class is updated with state from [K9] when [K9.saveSettingsAsync] is
 * called.
 */
// TODO(#9432): Split GeneralSettings and GeneralSettingsManager in smaller classes/interfaces
@Suppress("TooManyFunctions")
internal class RealGeneralSettingsManager(
    private val preferences: Preferences,
    private val coroutineScope: CoroutineScope,
    private val changePublisher: PreferenceChangePublisher,
    private val privacySettingsPreferenceManager: PrivacySettingsPreferenceManager,
    private val backgroundDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : GeneralSettingsManager {
    val mutex = Mutex()
    // TODO(#9432): Should be removed when K9 settings is completely migrated
    // This fallback is required until we finalize the split of the GeneralSettings class and Manager.
    // The GeneralSettings must be composed by other smaller Managers flows.
    private val k9GeneralSettingsFallback = MutableStateFlow(value = loadGeneralSettings())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val generalSettings = k9GeneralSettingsFallback
        .combine(privacySettingsPreferenceManager.getConfigFlow()) { generalSettings, privacySettings ->
            generalSettings.copy(
                privacy = privacySettings,
            )
        }
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = GeneralSettings(),
        )

    @Deprecated("This only exists for collaboration with the K9 class")
    val storage: Storage
        get() = preferences.storage

    @Deprecated(
        message = "Use PreferenceManager<GeneralSettings>.getConfig() instead",
        replaceWith = ReplaceWith(expression = "getConfig()"),
    )
    @Synchronized
    override fun getSettings(): GeneralSettings = generalSettings.value

    @Deprecated(
        message = "Use PreferenceManager<GeneralSettings>.getConfigFlow() instead",
        replaceWith = ReplaceWith(expression = "getConfigFlow()"),
    )
    override fun getSettingsFlow(): Flow<GeneralSettings> = generalSettings

    override fun getConfig(): GeneralSettings = generalSettings.value

    override fun getConfigFlow(): Flow<GeneralSettings> = generalSettings

    @Synchronized
    fun loadSettings() {
        K9.loadPrefs(preferences.storage)
        // TODO(#9232): Should be removed when K9 settings is completely migrated
        k9GeneralSettingsFallback.update { loadGeneralSettings() }
    }

    @Deprecated(message = "This only exists for collaboration with the K9 class")
    fun saveSettingsAsync() {
        coroutineScope.launch(backgroundDispatcher) {
            val settings = updateGeneralSettingsWithStateFromK9()
            save(config = settings)
        }
    }

    override fun save(config: GeneralSettings) {
        coroutineScope.launch(backgroundDispatcher) {
            mutex.withLock {
                saveSettings(settings = config)
                privacySettingsPreferenceManager.save(config.privacy)
            }
        }
    }

    @Synchronized
    @Deprecated("This only exists for collaboration with the K9 class and should be removed after #9232")
    private fun updateGeneralSettingsWithStateFromK9(): GeneralSettings {
        return getSettings().copy(
            backgroundSync = K9.backgroundOps.toBackgroundSync(),
        ).also { generalSettings ->
            k9GeneralSettingsFallback.update { generalSettings }
        }
    }

    @Synchronized
    private fun saveSettings(settings: GeneralSettings) {
        val editor = preferences.createStorageEditor()
        K9.save(editor)
        writeSettings(editor, settings)
        editor.commit()

        changePublisher.publish()
    }

    @Deprecated(message = "This function is being used within the setters and must be deleted after #9432")
    @Synchronized
    private fun GeneralSettings.persist() {
        saveSettingsAsync(this)
        // TODO(#9432): Should be removed when K9 settings is completely migrated
        k9GeneralSettingsFallback.update { this }
    }

    private fun saveSettingsAsync(generalSettings: GeneralSettings) {
        coroutineScope.launch(backgroundDispatcher) {
            saveSettings(generalSettings)
        }
    }

    @Synchronized
    override fun setShowRecentChanges(showRecentChanges: Boolean) {
        getSettings().copy(showRecentChanges = showRecentChanges).persist()
    }

    @Synchronized
    override fun setAppTheme(appTheme: AppTheme) {
        getSettings().copy(appTheme = appTheme).persist()
    }

    @Synchronized
    override fun setMessageViewTheme(subTheme: SubTheme) {
        getSettings().copy(messageViewTheme = subTheme).persist()
    }

    @Synchronized
    override fun setMessageComposeTheme(subTheme: SubTheme) {
        getSettings().copy(messageComposeTheme = subTheme).persist()
    }

    @Synchronized
    override fun setFixedMessageViewTheme(fixedMessageViewTheme: Boolean) {
        getSettings().copy(fixedMessageViewTheme = fixedMessageViewTheme).persist()
    }

    override fun setIsShowUnifiedInbox(isShowUnifiedInbox: Boolean) {
        getSettings().copy(isShowUnifiedInbox = isShowUnifiedInbox).persist()
    }

    override fun setIsShowStarredCount(isShowStarredCount: Boolean) {
        getSettings().copy(isShowStarredCount = isShowStarredCount).persist()
    }

    override fun setIsShowMessageListStars(isShowMessageListStars: Boolean) {
        getSettings().copy(isShowMessageListStars = isShowMessageListStars).persist()
    }

    override fun setIsShowAnimations(isShowAnimations: Boolean) {
        getSettings().copy(isShowAnimations = isShowAnimations).persist()
    }

    override fun setIsShowCorrespondentNames(isShowCorrespondentNames: Boolean) {
        getSettings().copy(isShowCorrespondentNames = isShowCorrespondentNames).persist()
    }

    @Synchronized
    override fun setSetupArchiveShouldNotShowAgain(shouldShowSetupArchiveFolderDialog: Boolean) {
        getSettings().copy(shouldShowSetupArchiveFolderDialog = shouldShowSetupArchiveFolderDialog).persist()
    }

    override fun setIsMessageListSenderAboveSubject(isMessageListSenderAboveSubject: Boolean) {
        getSettings().copy(isMessageListSenderAboveSubject = isMessageListSenderAboveSubject).persist()
    }

    override fun setIsShowContactName(isShowContactName: Boolean) {
        getSettings().copy(isShowContactName = isShowContactName).persist()
    }

    override fun setIsShowContactPicture(isShowContactPicture: Boolean) {
        getSettings().copy(isShowContactPicture = isShowContactPicture).persist()
    }

    override fun setIsChangeContactNameColor(isChangeContactNameColor: Boolean) {
        getSettings().copy(isChangeContactNameColor = isChangeContactNameColor).persist()
    }

    override fun setIsColorizeMissingContactPictures(isColorizeMissingContactPictures: Boolean) {
        getSettings().copy(isColorizeMissingContactPictures = isColorizeMissingContactPictures).persist()
    }

    override fun setIsUseBackgroundAsUnreadIndicator(isUseBackgroundAsUnreadIndicator: Boolean) {
        getSettings().copy(isUseBackgroundAsUnreadIndicator = isUseBackgroundAsUnreadIndicator).persist()
    }

    override fun setIsShowComposeButtonOnMessageList(isShowComposeButtonOnMessageList: Boolean) {
        getSettings().copy(isShowComposeButtonOnMessageList = isShowComposeButtonOnMessageList).persist()
    }

    override fun setIsThreadedViewEnabled(isThreadedViewEnabled: Boolean) {
        getSettings().copy(isThreadedViewEnabled = isThreadedViewEnabled).persist()
    }

    override fun setIsUseMessageViewFixedWidthFont(isUseMessageViewFixedWidthFont: Boolean) {
        getSettings().copy(isUseMessageViewFixedWidthFont = isUseMessageViewFixedWidthFont).persist()
    }

    override fun setIsAutoFitWidth(isAutoFitWidth: Boolean) {
        getSettings().copy(isAutoFitWidth = isAutoFitWidth).persist()
    }

    override fun setQuietTimeEnds(quietTimeEnds: String) {
        getSettings().copy(quietTimeEnds = quietTimeEnds).persist()
    }

    override fun setQuietTimeStarts(quietTimeStarts: String) {
        getSettings().copy(quietTimeStarts = quietTimeStarts).persist()
    }

    override fun setIsQuietTimeEnabled(isQuietTimeEnabled: Boolean) {
        getSettings().copy(isQuietTimeEnabled = isQuietTimeEnabled).persist()
    }

    private fun writeSettings(editor: StorageEditor, settings: GeneralSettings) {
        editor.putBoolean("showRecentChanges", settings.showRecentChanges)
        editor.putEnum("theme", settings.appTheme)
        editor.putEnum("messageViewTheme", settings.messageViewTheme)
        editor.putEnum("messageComposeTheme", settings.messageComposeTheme)
        editor.putBoolean("fixedMessageViewTheme", settings.fixedMessageViewTheme)
        editor.putBoolean("showUnifiedInbox", settings.isShowUnifiedInbox)
        editor.putBoolean("showStarredCount", settings.isShowStarredCount)
        editor.putBoolean("messageListStars", settings.isShowMessageListStars)
        editor.putBoolean("animations", settings.isShowAnimations)
        editor.putBoolean("showCorrespondentNames", settings.isShowCorrespondentNames)
        editor.putBoolean(KEY_SHOULD_SHOW_SETUP_ARCHIVE_FOLDER_DIALOG, settings.shouldShowSetupArchiveFolderDialog)
        editor.putBoolean("messageListSenderAboveSubject", settings.isMessageListSenderAboveSubject)
        editor.putBoolean("showContactName", settings.isShowContactName)
        editor.putBoolean("showContactPicture", settings.isShowContactPicture)
        editor.putBoolean(KEY_CHANGE_REGISTERED_NAME_COLOR, settings.isChangeContactNameColor)
        editor.putBoolean(KEY_COLORIZE_MISSING_CONTACT_PICTURE, settings.isColorizeMissingContactPictures)
        editor.putBoolean(KEY_USE_BACKGROUND_AS_UNREAD_INDICATOR, settings.isUseBackgroundAsUnreadIndicator)
        editor.putBoolean(KEY_SHOW_COMPOSE_BUTTON_ON_MESSAGE_LIST, settings.isShowComposeButtonOnMessageList)
        editor.putBoolean(KEY_THREAD_VIEW_ENABLED, settings.isThreadedViewEnabled)
        editor.putBoolean(KEY_MESSAGE_VIEW_FIXED_WIDTH_FONT, settings.isUseMessageViewFixedWidthFont)
        editor.putBoolean(KEY_AUTO_FIT_WIDTH, settings.isAutoFitWidth)
        editor.putString(KEY_QUIET_TIME_ENDS, settings.quietTimeEnds)
        editor.putString(KEY_QUIET_TIME_STARTS, settings.quietTimeStarts)
        editor.putBoolean(KEY_QUIET_TIME_ENABLED, settings.isQuietTimeEnabled)
    }

    private fun loadGeneralSettings(): GeneralSettings {
        val storage = preferences.storage

        val quietTimeEnds = storage.getStringOrDefault(KEY_QUIET_TIME_ENDS, "7:00")
        val quietTimeStarts = storage.getStringOrDefault(KEY_QUIET_TIME_STARTS, "21:00")
        val isQuietTimeEnabled = storage.getBoolean(KEY_QUIET_TIME_ENABLED, false)

        val settings = GeneralSettings(
            backgroundSync = K9.backgroundOps.toBackgroundSync(),
            showRecentChanges = storage.getBoolean("showRecentChanges", true),
            appTheme = storage.getEnum("theme", AppTheme.FOLLOW_SYSTEM),
            messageViewTheme = storage.getEnum(
                "messageViewTheme",
                SubTheme.USE_GLOBAL,
            ),
            messageComposeTheme = storage.getEnum(
                "messageComposeTheme",
                SubTheme.USE_GLOBAL,
            ),
            fixedMessageViewTheme = storage.getBoolean("fixedMessageViewTheme", true),
            isShowUnifiedInbox = storage.getBoolean("showUnifiedInbox", false),
            isShowStarredCount = storage.getBoolean("showStarredCount", false),
            isShowMessageListStars = storage.getBoolean("messageListStars", true),
            isShowAnimations = storage.getBoolean("animations", true),
            isShowCorrespondentNames = storage.getBoolean("showCorrespondentNames", true),
            shouldShowSetupArchiveFolderDialog = storage.getBoolean(
                key = KEY_SHOULD_SHOW_SETUP_ARCHIVE_FOLDER_DIALOG,
                defValue = true,
            ),
            isMessageListSenderAboveSubject = storage.getBoolean("messageListSenderAboveSubject", false),
            isShowContactName = storage.getBoolean("showContactName", false),
            isShowContactPicture = storage.getBoolean("showContactPicture", true),
            isColorizeMissingContactPictures = storage.getBoolean(KEY_COLORIZE_MISSING_CONTACT_PICTURE, true),
            isChangeContactNameColor = storage.getBoolean(KEY_CHANGE_REGISTERED_NAME_COLOR, false),
            isUseBackgroundAsUnreadIndicator = storage.getBoolean(KEY_USE_BACKGROUND_AS_UNREAD_INDICATOR, false),
            isShowComposeButtonOnMessageList = storage.getBoolean(KEY_SHOW_COMPOSE_BUTTON_ON_MESSAGE_LIST, true),
            isThreadedViewEnabled = storage.getBoolean(KEY_THREAD_VIEW_ENABLED, true),
            isUseMessageViewFixedWidthFont = storage.getBoolean(KEY_MESSAGE_VIEW_FIXED_WIDTH_FONT, false),
            isAutoFitWidth = storage.getBoolean(KEY_AUTO_FIT_WIDTH, true),
            quietTimeEnds = quietTimeEnds,
            quietTimeStarts = quietTimeStarts,
            isQuietTimeEnabled = isQuietTimeEnabled,
            isQuietTime = getIsQuietTime(isQuietTimeEnabled, quietTimeStarts, quietTimeEnds),
        )
        return settings
    }

    private fun getIsQuietTime(isQuietTimeEnabled: Boolean, quietTimeStarts: String, quietTimeEnds: String): Boolean {
        if (!isQuietTimeEnabled) return false

        val clock = DI.get<Clock>()
        val quietTimeChecker = QuietTimeChecker(
            clock = clock,
            quietTimeStart = quietTimeStarts,
            quietTimeEnd = quietTimeEnds,
        )
        return quietTimeChecker.isQuietTime
    }
}

private fun K9.BACKGROUND_OPS.toBackgroundSync(): BackgroundSync {
    return when (this) {
        K9.BACKGROUND_OPS.ALWAYS -> BackgroundSync.ALWAYS
        K9.BACKGROUND_OPS.NEVER -> BackgroundSync.NEVER
        K9.BACKGROUND_OPS.WHEN_CHECKED_AUTO_SYNC -> BackgroundSync.FOLLOW_SYSTEM_AUTO_SYNC
    }
}

private inline fun <reified T : Enum<T>> Storage.getEnum(key: String, defaultValue: T): T {
    return try {
        getEnumOrDefault(key, defaultValue)
    } catch (e: Exception) {
        Log.e(e, "Couldn't read setting '%s'. Using default value instead.", key)
        defaultValue
    }
}
