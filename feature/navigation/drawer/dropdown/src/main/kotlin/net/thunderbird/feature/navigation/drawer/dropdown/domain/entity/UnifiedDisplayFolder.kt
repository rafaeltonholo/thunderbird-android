package net.thunderbird.feature.navigation.drawer.dropdown.domain.entity

data class UnifiedDisplayFolder(
    override val id: String,
    val unifiedType: UnifiedDisplayFolderType,
    override val unreadMessageCount: Int,
    override val starredMessageCount: Int,
) : DisplayFolder
