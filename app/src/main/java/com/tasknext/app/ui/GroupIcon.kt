package com.tasknext.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector

fun groupIconVector(key: String): ImageVector = when (key) {
    "task" -> Icons.Default.CheckCircle
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
    "car" -> Icons.Default.DirectionsCar
    "calendar" -> Icons.Default.Event
    "flag" -> Icons.Default.Flag
    "bolt" -> Icons.Default.Bolt
    "place" -> Icons.Default.Place
    "person" -> Icons.Default.Person
    "phone" -> Icons.Default.Phone
    "mail" -> Icons.Default.Mail
    "build" -> Icons.Default.Build
    "palette" -> Icons.Default.Palette
    "idea" -> Icons.Default.Lightbulb
    "public" -> Icons.Default.Public
    "bookmark" -> Icons.Default.Bookmark
    "label" -> Icons.Default.Label
    "folder" -> Icons.Default.Folder
    "schedule" -> Icons.Default.Schedule
    "circle" -> Icons.Default.RadioButtonUnchecked
    "list" -> Icons.Default.ListAlt
    "computer" -> Icons.Default.Computer
    "money" -> Icons.Default.AttachMoney
    "headphones" -> Icons.Default.Headphones
    "camera" -> Icons.Default.CameraAlt
    "lock" -> Icons.Default.Lock
    "search" -> Icons.Default.Search
    "refresh" -> Icons.Default.Refresh
    "share" -> Icons.Default.Share
    "send" -> Icons.Default.Send
    "notifications" -> Icons.Default.Notifications
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
