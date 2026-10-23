package io.github.greg7gkb.iworkout.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.github.greg7gkb.iworkout.R
import io.github.greg7gkb.iworkout.data.local.ExerciseType
import java.net.URLEncoder

// A card for an Exercise, striped with its type's color (see ExerciseType.accent()).
// `dimmed` fades the content once an exercise is complete, so the next one stands out.
@Composable
fun ExerciseCard(
    type: ExerciseType,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) = StripeCard(type.accent(), modifier, dimmed, onClick, content)

// A card with a colored stripe down its left edge.
@Composable
fun StripeCard(
    stripe: Color,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val body: @Composable () -> Unit = {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(8.dp).fillMaxHeight().background(stripe))
            Column(Modifier.weight(1f).alpha(if (dimmed) 0.6f else 1f), content = content)
        }
    }
    // Raised card with full-contrast text (the default card color is muted in this palette).
    val colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    )
    if (onClick != null) Card(onClick = onClick, modifier = modifier.fillMaxWidth(), colors = colors) { body() }
    else Card(modifier.fillMaxWidth(), colors = colors) { body() }
}

// Top-bar icon action, sized to match the app's large text.
@Composable
fun BarIcon(@DrawableRes icon: Int, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(56.dp)) {
        Icon(painterResource(icon), contentDescription = description, modifier = Modifier.size(30.dp))
    }
}

// Ways to look up how to do an Exercise; the label is the menu text, the query is what Google gets.
// Each query says "exercise" so ambiguous names ("Dead Bug", "Bird Dog") don't return unrelated results.
internal enum class ExerciseLookup(val label: String, val query: (String) -> String) {
    SEARCH("Search", { "$it exercise" }),
    IMAGES("Images", { "$it exercise images" }),
    HOW_TO("How to do it", { "how to do $it exercise" }),
}

internal fun googleSearchUrl(query: String) = "https://www.google.com/search?q=" + URLEncoder.encode(query, "UTF-8")

// An Exercise's name; tapping it offers a Google search for how to do it.
@Composable
fun ExerciseTitle(name: String, style: TextStyle, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier) {
        // The trailing search icon marks names that can be looked up (warm-up and cool-down can't).
        Row(
            Modifier.clickable(onClickLabel = "Look up $name", role = Role.Button) { menuOpen = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(name, style = style, modifier = Modifier.weight(1f, fill = false))
            Icon(
                painterResource(R.drawable.ic_search),
                contentDescription = null,
                modifier = Modifier.padding(start = 8.dp).size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            ExerciseLookup.entries.forEach { lookup ->
                DropdownMenuItem(
                    text = { Text(lookup.label) },
                    onClick = {
                        menuOpen = false
                        // No browser installed: nothing to open, so ignore the tap.
                        runCatching { uriHandler.openUri(googleSearchUrl(lookup.query(name))) }
                    },
                )
            }
        }
    }
}
