package chat.revolt.internals

import android.content.Context
import chat.peptide.R
import chat.revolt.RevoltApplication
import chat.revolt.api.RevoltAPI
import chat.revolt.api.RevoltJson
import chat.revolt.api.schemas.Server
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

@Serializable
data class Emoji(
    val base: List<Long>,
    val alternates: List<List<Long>>,
    val emoticons: List<String>,
    val shortcodes: List<String>,
    val animated: Boolean
)

@Serializable
data class EmojiGroup(
    val group: String,
    val emoji: List<Emoji>
)

enum class FitzpatrickSkinTone(val modifierCodepoint: Int?) {
    None(null),
    Light(0x1F3FB),
    MediumLight(0x1F3FC),
    Medium(0x1F3FD),
    MediumDark(0x1F3FE),
    Dark(0x1F3FF)
}

enum class UnicodeEmojiSection(val googleName: String, val nameResource: Int) {
    Smileys("Smileys and emotions", R.string.emoji_category_smileys),
    People("People", R.string.emoji_category_people),
    Animals("Animals and nature", R.string.emoji_category_animals),
    Food("Food and drink", R.string.emoji_category_food),
    Travel("Travel and places", R.string.emoji_category_travel),
    Activities("Activities and events", R.string.emoji_category_activities),
    Objects("Objects", R.string.emoji_category_objects),
    Symbols("Symbols", R.string.emoji_category_symbols),
    Flags("Flags", R.string.emoji_category_flags)
}

sealed class Category {
    data class UnicodeEmojiCategory(val definition: UnicodeEmojiSection) : Category()
    data class ServerEmoteCategory(val server: Server) : Category()
}

sealed class EmojiPickerItem {
    data class Section(val category: Category) : EmojiPickerItem()
    data class UnicodeEmoji(
        val character: String,
        val hasSkinTones: Boolean,
        val alternates: List<List<Long>>
    ) : EmojiPickerItem()

    data class ServerEmote(val emote: chat.revolt.api.schemas.Emoji) : EmojiPickerItem()
}

class EmojiImpl {
    private var metadata: List<EmojiGroup>

    private fun initMetadata(context: Context): List<EmojiGroup> {
        val json = context.assets.open("metadata/emoji.json").use {
            it.reader().readText()
        }
        return RevoltJson.decodeFromString(ListSerializer(EmojiGroup.serializer()), json)
    }

    fun serversWithEmotes(): List<Server> {
        return RevoltAPI
            .emojiCache
            .values
            .asSequence()
            .map { it.parent }
            .filterNotNull()
            .filter { it.type == "Server" }
            .map { it.id }
            .distinct()
            .mapNotNull { RevoltAPI.serverCache[it] }
            .toList()
    }

    fun serverEmoteList(server: Server): List<EmojiPickerItem> {
        val list = mutableListOf<EmojiPickerItem>()
        val emotes = RevoltAPI.emojiCache.values.filter { it.parent?.id == server.id }

        list.add(EmojiPickerItem.Section(Category.ServerEmoteCategory(server)))
        list.addAll(emotes.map { EmojiPickerItem.ServerEmote(it) })

        return list
    }

    fun flatPickerList(): List<EmojiPickerItem> {
        val list = mutableListOf<EmojiPickerItem>()

        for (server in serversWithEmotes()) {
            list.addAll(serverEmoteList(server))
        }

        for (group in metadata) {
            val category =
                UnicodeEmojiSection.entries.find { it.googleName == group.group } ?: continue
            list.add(EmojiPickerItem.Section(Category.UnicodeEmojiCategory(category)))
            list.addAll(
                group.emoji.map { emoji ->
                    EmojiPickerItem.UnicodeEmoji(
                        emoji.base.joinToString("") { String(Character.toChars(it.toInt())) },
                        emoji.alternates.any { alternate ->
                            alternate.any { codepoint ->
                                codepoint in 0x1F3FB..0x1F3FF
                            }
                        },
                        emoji.alternates
                    )
                }
            )
        }

        return list
    }

    /**
     * Returns a map of category to start and end index of the category in the flat picker list
     * Impl
     * ====
     * 1. Iterate through servers that have emotes. Get the index of the server emote category.
     * 2. Get all emotes in that server. Add the size of that list to the index of the server emote category.
     * 3. Push Pair(index, index + size) to the map.
     * 4. Iterate through all unicode emoji categories. Get the index of the category.
     * Unless it's the last category {
     * 5.1. Get the index of the next category. Subtract 1 from that index.
     * 5.2. Push Pair(index, lastIndex) to the map.
     * } Otherwise {
     * 5. Push Pair(index, Int.MAX_VALUE) to the map.
     * }
     * 6. Return the map.
     */
    fun categorySpans(flatPickerList: List<EmojiPickerItem>): Map<Category, Pair<Int, Int>> {
        val output = mutableMapOf<Category, Pair<Int, Int>>()

        for (server in serversWithEmotes()) {
            val index =
                flatPickerList.indexOfFirst {
                    it is EmojiPickerItem.Section && it.category is Category.ServerEmoteCategory && it.category.server == server
                }
            val allEmotesInThatServer =
                RevoltAPI.emojiCache.values.filter { it.parent?.id == server.id }
            val lastIndex = index + allEmotesInThatServer.size

            output[Category.ServerEmoteCategory(server)] = Pair(index, lastIndex)
        }
        for (section in UnicodeEmojiSection.entries) {
            val index =
                flatPickerList.indexOfFirst {
                    it is EmojiPickerItem.Section && it.category is Category.UnicodeEmojiCategory && it.category.definition == section
                }
            val lastIndex = if (section == UnicodeEmojiSection.entries.last()) {
                Int.MAX_VALUE
            } else {
                val nextSection = UnicodeEmojiSection.entries[section.ordinal + 1]
                flatPickerList.indexOfFirst {
                    it is EmojiPickerItem.Section && it.category is Category.UnicodeEmojiCategory && it.category.definition == nextSection
                } - 1
            }
            output[Category.UnicodeEmojiCategory(section)] = Pair(index, lastIndex)
        }

        return output
    }

    /**
     * All of our unicode emoji are the base variant with no modifiers applied by default.
     * This function returns the unicode emoji with the modifier from the specified skin type applied.
     */
    fun applyFitzpatrickSkinTone(
        item: EmojiPickerItem.UnicodeEmoji,
        skinType: FitzpatrickSkinTone
    ): String {
        if (!item.hasSkinTones || skinType == FitzpatrickSkinTone.None) return item.character

        // HACK: We simply find the modifier version from metadata that
        // contains the skin tone modifier codepoint.
        val modifier = item.alternates.maxByOrNull { alternate ->
            // HACK HACK: We find the alternate with the most frequency of our skin tone modifier.
            // This is because some emoji have multiple skin tone modifier and we are taking the
            // easy way here by only allowing a single skin tone change. This is not ideal.
            // Users are encouraged to use the system emoji keyboard to get the full range of
            // skin tone modifiers.
            alternate.count { it == skinType.modifierCodepoint?.toLong() }
        }

        return modifier?.joinToString("") { String(Character.toChars(it.toInt())) }
            ?: item.character
    }

    /**
     * Perform a search on the flat picker list to find all custom and unicode emoji that match the
     * query.
     */
    fun searchForEmoji(query: String): List<EmojiPickerItem> {
        val list = mutableListOf<EmojiPickerItem>()

        for (server in serversWithEmotes()) {
            val emotes = RevoltAPI.emojiCache.values.filter { it.parent?.id == server.id }
            val matchingEmotes =
                emotes.filter { it.name?.contains(query, ignoreCase = true) ?: false }
            if (matchingEmotes.isNotEmpty()) {
                list.add(EmojiPickerItem.Section(Category.ServerEmoteCategory(server)))
                list.addAll(matchingEmotes.map { EmojiPickerItem.ServerEmote(it) })
            }
        }

        for (group in metadata) {
            val matchingEmoji = group.emoji.filter {
                it.shortcodes.any { code ->
                    code.contains(
                        query,
                        ignoreCase = true
                    )
                }
            }
            if (matchingEmoji.isNotEmpty()) {
                val category =
                    UnicodeEmojiSection.entries.find { it.googleName == group.group } ?: continue
                list.add(EmojiPickerItem.Section(Category.UnicodeEmojiCategory(category)))
                list.addAll(
                    matchingEmoji.map { emoji ->
                        EmojiPickerItem.UnicodeEmoji(
                            emoji.base.joinToString("") { String(Character.toChars(it.toInt())) },
                            emoji.alternates.any { alternate ->
                                alternate.any { codepoint ->
                                    codepoint in 0x1F3FB..0x1F3FF
                                }
                            },
                            emoji.alternates
                        )
                    }
                )
            }
        }

        return list
    }

    fun unicodeByShortcode(shortcode: String): String? {
        return metadata.asSequence().mapNotNull { group ->
            group.emoji.find { emoji ->
                emoji.shortcodes.any { code ->
                    code == ":${shortcode}:"
                }
            }
        }.firstOrNull().let { emoji ->
            emoji?.base?.joinToString("") { String(Character.toChars(it.toInt())) }
        }
    }

    fun shortcodeContains(query: String): List<Emoji> {
        return metadata.asSequence().map { group ->
            group.emoji.filter { emoji ->
                emoji.shortcodes.any { code ->
                    code.contains(query, ignoreCase = true)
                }
            }
        }.flatten().toList()
    }

    fun unicodeAsShortcode(unicode: String): String? {
        return metadata.asSequence().mapNotNull { group ->
            group.emoji.find { emoji ->
                emoji.base.joinToString("") { String(Character.toChars(it.toInt())) } == unicode
            }
        }.firstOrNull().let { emoji ->
            emoji?.shortcodes?.firstOrNull()
        }
    }

    fun codepointIsEmoji(codepoint: Int): Boolean {
        return metadata.any { group ->
            group.emoji.any { emoji ->
                emoji.base.contains(codepoint.toLong()) || emoji.alternates.any { alternate ->
                    alternate.contains(codepoint.toLong())
                }
            }
        }
    }

    init {
        metadata = initMetadata(RevoltApplication.instance.applicationContext)
    }
}
