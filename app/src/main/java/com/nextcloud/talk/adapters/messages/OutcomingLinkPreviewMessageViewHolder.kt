/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2017-2019 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.adapters.messages

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import autodagger.AutoInjector
import coil.load
import com.nextcloud.android.common.ui.theme.utils.ColorRole
import com.nextcloud.talk.R
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.databinding.ItemCustomOutcomingLinkPreviewMessageBinding
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.nextcloud.talk.models.json.chat.ReadStatus
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.DateUtils
import com.nextcloud.talk.utils.message.MessageUtils
import com.nextcloud.talk.utils.preferences.AppPreferences
import com.stfalcon.chatkit.messages.MessageHolders
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class OutcomingLinkPreviewMessageViewHolder(outcomingView: View, payload: Any) :
    MessageHolders.OutcomingTextMessageViewHolder<ChatMessage>(outcomingView, payload) {

    private val binding: ItemCustomOutcomingLinkPreviewMessageBinding =
        ItemCustomOutcomingLinkPreviewMessageBinding.bind(itemView)

    @Inject
    lateinit var context: Context

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    @Inject
    lateinit var messageUtils: MessageUtils

    @Inject
    lateinit var dateUtils: DateUtils

    @Inject
    lateinit var appPreferences: AppPreferences

    @Inject
    lateinit var ncApi: NcApi

    lateinit var message: ChatMessage

    lateinit var commonMessageInterface: CommonMessageInterface

    @SuppressLint("SetTextI18n")
    override fun onBind(message: ChatMessage) {
        super.onBind(message)
        this.message = message
        sharedApplication!!.componentApplication.inject(this)
        viewThemeUtils.platform.colorTextView(binding.messageTime, ColorRole.ON_SURFACE_VARIANT)
        binding.messageTime.text = dateUtils.getLocalTimeStringFromTimestamp(message.timestamp)

        colorizeMessageBubble(message)
        var processedMessageText =
            messageUtils.enrichChatMessageText(binding.messageText.context, message, false, viewThemeUtils)
        processedMessageText = messageUtils.processMessageParameters(
            binding.messageText.context,
            viewThemeUtils,
            processedMessageText!!,
            message,
            itemView
        )

        binding.messageText.text = processedMessageText

        itemView.isSelected = false

        // parent message handling
        setParentMessageDataOnMessageItem(message)

        val readStatusDrawableInt = when (message.readStatus) {
            ReadStatus.READ -> R.drawable.ic_check_all
            ReadStatus.SENT -> R.drawable.ic_check
            else -> null
        }

        val readStatusContentDescriptionString = when (message.readStatus) {
            ReadStatus.READ -> context.resources?.getString(R.string.nc_message_read)
            ReadStatus.SENT -> context.resources?.getString(R.string.nc_message_sent)
            else -> null
        }

        readStatusDrawableInt?.let { drawableInt ->
            AppCompatResources.getDrawable(context, drawableInt)?.let {
                binding.checkMark.setImageDrawable(it)
                viewThemeUtils.talk.themeMessageCheckMark(binding.checkMark)
            }
        }

        binding.checkMark.contentDescription = readStatusContentDescriptionString

        LinkPreview().showLink(
            message,
            ncApi,
            binding.referenceInclude,
            itemView.context
        )
        binding.referenceInclude.referenceWrapper.setOnLongClickListener { l: View? ->
            commonMessageInterface.onOpenMessageActionsDialog(message)
            true
        }

        itemView.setTag(R.string.replyable_message_view_tag, message.replyable)

        Reaction().showReactions(
            message,
            ::clickOnReaction,
            ::longClickOnReaction,
            binding.reactions,
            binding.messageTime.context,
            true,
            viewThemeUtils
        )
    }

    private fun longClickOnReaction(chatMessage: ChatMessage) {
        commonMessageInterface.onLongClickReactions(chatMessage)
    }

    private fun clickOnReaction(chatMessage: ChatMessage, emoji: String) {
        commonMessageInterface.onClickReaction(chatMessage, emoji)
    }

    private fun setParentMessageDataOnMessageItem(message: ChatMessage) {
        if (!message.isDeleted && message.parentMessage != null) {
            val parentChatMessage = message.parentMessage
            parentChatMessage!!.activeUser = message.activeUser
            parentChatMessage.imageUrl?.let {
                binding.messageQuote.quotedMessageImage.visibility = View.VISIBLE
                binding.messageQuote.quotedMessageImage.load(it) {
                    addHeader(
                        "Authorization",
                        ApiUtils.getCredentials(message.activeUser!!.username, message.activeUser!!.token)!!
                    )
                }
            } ?: run {
                binding.messageQuote.quotedMessageImage.visibility = View.GONE
            }
            binding.messageQuote.quotedMessageAuthor.text = parentChatMessage.actorDisplayName
                ?: context.getText(R.string.nc_nick_guest)
            binding.messageQuote.quotedMessage.text = messageUtils
                .enrichChatReplyMessageText(
                    binding.messageQuote.quotedMessage.context,
                    parentChatMessage,
                    false,
                    viewThemeUtils
                )
            viewThemeUtils.talk.colorOutgoingQuoteText(binding.messageQuote.quotedMessage)
            viewThemeUtils.talk.colorOutgoingQuoteAuthorText(binding.messageQuote.quotedMessageAuthor)
            viewThemeUtils.talk.colorOutgoingQuoteBackground(binding.messageQuote.quoteColoredView)

            binding.messageQuote.quotedChatMessageView.visibility = View.VISIBLE
        } else {
            binding.messageQuote.quotedChatMessageView.visibility = View.GONE
        }
    }

    private fun colorizeMessageBubble(message: ChatMessage) {
        viewThemeUtils.talk.themeOutgoingMessageBubble(bubble, message.isGrouped, message.isDeleted)
    }

    fun assignCommonMessageInterface(commonMessageInterface: CommonMessageInterface) {
        this.commonMessageInterface = commonMessageInterface
    }

    companion object {
        private val TAG = OutcomingLinkPreviewMessageViewHolder::class.java.simpleName
    }
}
