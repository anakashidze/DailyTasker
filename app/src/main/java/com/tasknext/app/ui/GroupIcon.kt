package com.tasknext.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector

fun groupIconVector(key: String): ImageVector = when (key) {
    "home" -> Icons.Default.Home
    "health" -> Icons.Default.Favorite
    "work" -> Icons.Default.Work
    "star" -> Icons.Default.Star
    "fitness" -> Icons.Default.FitnessCenter
    "bike" -> Icons.Default.DirectionsBike
    "shopping" -> Icons.Default.ShoppingCart
    "study" -> Icons.Default.School
    "food" -> Icons.Default.Restaurant
    "travel" -> Icons.Default.Flight
    "pet" -> Icons.Default.Pets
    "book" -> Icons.Default.MenuBook
    else -> Icons.Default.CheckCircle
}

@Composable
fun GroupIcon(
    key: String,
    contentDescription: String?,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Icon(
        imageVector = groupIconVector(key),
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier
    )
}
