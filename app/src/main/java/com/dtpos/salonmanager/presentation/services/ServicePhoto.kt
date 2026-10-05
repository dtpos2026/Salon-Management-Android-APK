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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.dtpos.salonmanager.presentation.common.LocalAppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A service's menu photo (loaded off the main thread), or a scissors placeholder. */
@Composable
fun ServicePhoto(path: String?, modifier: Modifier = Modifier, maxSize: Int = 360) {
    val store = LocalAppContainer.current.serviceImageStore
    var image by remember(path, maxSize) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(path, maxSize) {
        image = if (path == null) null else withContext(Dispatchers.IO) { store.loadBitmap(path, maxSize)?.asImageBitmap() }
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
