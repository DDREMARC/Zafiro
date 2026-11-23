package com.dremarc.grokbot.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dremarc.grokbot.domain.model.Conversation

@Composable
fun ConversationDrawer(
    conversations: List<Conversation>,
    activeId: String?,
    onNewChat: () -> Unit,
    onSelect: (String) -> Unit,
    onRename: (Conversation) -> Unit,
    onDelete: (Conversation) -> Unit,
    onOpenSettings: () -> Unit,
) {
    ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
        Text(
            text = "Z4firo",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 28.dp, top = 24.dp, bottom = 12.dp),
        )
        NavigationDrawerItem(
            label = { Text("Nuevo chat") },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            selected = false,
            onClick = onNewChat,
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        if (conversations.isEmpty()) {
            Text(
                text = "Aún no tienes conversaciones.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
            )
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(conversations, key = { it.id }) { conversation ->
                ConversationRow(
                    conversation = conversation,
                    selected = conversation.id == activeId,
                    onSelect = { onSelect(conversation.id) },
                    onRename = { onRename(conversation) },
                    onDelete = { onDelete(conversation) },
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        NavigationDrawerItem(
            label = { Text("Ajustes") },
            icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
            selected = false,
            onClick = onOpenSettings,
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
        )
    }
}

@Composable
private fun ConversationRow(
    conversation: Conversation,
    selected: Boolean,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .background(
                color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                shape = RoundedCornerShape(24.dp),
            )
            .clickable(onClick = onSelect)
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = conversation.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRename) {
            Icon(Icons.Filled.Edit, contentDescription = "Renombrar")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Eliminar")
        }
    }
}
