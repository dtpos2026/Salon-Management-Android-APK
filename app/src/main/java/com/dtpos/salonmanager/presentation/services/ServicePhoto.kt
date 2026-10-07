package com.dtpos.salonmanager.presentation.services

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.dtpos.salonmanager.presentation.common.LocalAppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A service's menu photo, or a scissors placeholder. Photos already in memory show at once
 * (smooth scrolling); others are read off the main thread.
 */
@Composable
fun ServicePhoto(path: String?, modifier: Modifier = Modifier, maxSize: Int = 360) {
    val store = LocalAppContainer.current.serviceImageStore
    var image by remember(path, maxSize) { mutableStateOf(store.cached(path, maxSize)?.asImageBitmap()) }
    LaunchedEffect(path, maxSize) {
        if (image == null && path != null) image = withContext(Dispatchers.IO) { store.loadBitmap(path, maxSize)?.asImageBitmap() }
    }
    Box(modifier.background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
        val bitmap = image
        if (bitmap != null) {
            Image(bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Filled.ContentCut, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(28.dp))
        }
    }
}
