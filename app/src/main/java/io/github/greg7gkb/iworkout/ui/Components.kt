package io.github.greg7gkb.iworkout.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.github.greg7gkb.iworkout.data.local.ExerciseType

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
