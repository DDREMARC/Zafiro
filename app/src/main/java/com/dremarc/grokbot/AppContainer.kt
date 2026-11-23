package com.dremarc.grokbot

import android.content.Context
import com.dremarc.grokbot.data.imagegen.PollinationsImageGenerator
import com.dremarc.grokbot.data.local.AppDatabase
import com.dremarc.grokbot.data.local.RoomConversationRepository
import com.dremarc.grokbot.data.media.AndroidMediaRepository
import com.dremarc.grokbot.data.remote.AnthropicAssistantRepository
import com.dremarc.grokbot.data.settings.EncryptedSettingsStore
import com.dremarc.grokbot.domain.imagegen.ImageGenerator
import com.dremarc.grokbot.domain.repository.AssistantRepository
import com.dremarc.grokbot.domain.repository.ConversationRepository
import com.dremarc.grokbot.domain.repository.MediaRepository
import com.dremarc.grokbot.domain.repository.SettingsRepository
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Manual composition root: creates the data layer once and hands it to the ViewModels. */
class AppContainer(context: Context) {

    // Streaming responses can be quiet for a while, so the read timeout is generous.
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val database = AppDatabase.create(context)

    val settingsRepository: SettingsRepository = EncryptedSettingsStore(context)
    val conversationRepository: ConversationRepository = RoomConversationRepository(database)
    val assistantRepository: AssistantRepository = AnthropicAssistantRepository(httpClient)
    val imageGenerator: ImageGenerator = PollinationsImageGenerator(httpClient)
    val mediaRepository: MediaRepository = AndroidMediaRepository(context.applicationContext)
}
