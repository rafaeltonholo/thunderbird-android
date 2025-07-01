package net.thunderbird.feature.navigation.drawer.dropdown.domain.entity

/**
 * Represents a unified folder in the drawer.
 *
 * The id is unique for each unified folder type.
 */
enum class UnifiedDisplayFolderType(
    val id: String,
) {
    INBOX("unified_inbox"),
}
