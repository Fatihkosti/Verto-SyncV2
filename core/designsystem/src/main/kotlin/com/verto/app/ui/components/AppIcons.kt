package com.verto.app.ui.components

import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.*
import androidx.compose.ui.unit.dp
import com.verto.app.ui.theme.WhatsAppBrand

val WhatsAppIcon: ImageVector by lazy {
    ImageVector.Builder(
        name           = "WhatsApp",
        defaultWidth   = 24.dp,
        defaultHeight  = 24.dp,
        viewportWidth  = 24f,
        viewportHeight = 24f
    ).apply {
        // الدائرة الخلفية
        path(fill = SolidColor(WhatsAppBrand)) {
            moveTo(12f, 2f)
            arcTo(10f, 10f, 0f, false, true, 22f, 12f)
            arcTo(10f, 10f, 0f, false, true, 12f, 22f)
            arcTo(10f, 10f, 0f, false, true, 2f, 12f)
            arcTo(10f, 10f, 0f, false, true, 12f, 2f)
            close()
        }
        // فقاعة الكلام
        path(fill = SolidColor(Color.White)) {
            moveTo(12f, 5.5f)
            curveTo(8.41f, 5.5f, 5.5f, 8.41f, 5.5f, 12f)
            curveTo(5.5f, 13.22f, 5.86f, 14.36f, 6.47f, 15.32f)
            lineTo(5.5f, 18.5f)
            lineTo(8.78f, 17.54f)
            curveTo(9.71f, 18.11f, 10.81f, 18.45f, 12f, 18.45f)
            curveTo(15.59f, 18.45f, 18.5f, 15.54f, 18.5f, 11.95f)
            curveTo(18.5f, 8.36f, 15.59f, 5.5f, 12f, 5.5f)
            close()
        }
        // الهاتف داخل الفقاعة
        path(fill = SolidColor(WhatsAppBrand)) {
            moveTo(15.8f, 13.9f)
            curveTo(15.63f, 14.31f, 14.89f, 14.68f, 14.54f, 14.71f)
            curveTo(14.19f, 14.74f, 13.85f, 14.88f, 12.03f, 14.15f)
            curveTo(9.87f, 13.27f, 8.5f, 11.07f, 8.39f, 10.92f)
            curveTo(8.28f, 10.77f, 7.5f, 9.72f, 7.5f, 8.63f)
            curveTo(7.5f, 7.54f, 8.06f, 7.01f, 8.27f, 6.79f)
            curveTo(8.45f, 6.61f, 8.7f, 6.52f, 8.94f, 6.52f)
            curveTo(9.02f, 6.52f, 9.09f, 6.52f, 9.16f, 6.52f)
            curveTo(9.39f, 6.53f, 9.51f, 6.55f, 9.66f, 6.91f)
            curveTo(9.84f, 7.34f, 10.27f, 8.43f, 10.32f, 8.54f)
            curveTo(10.37f, 8.65f, 10.4f, 8.78f, 10.32f, 8.93f)
            curveTo(10.24f, 9.08f, 10.19f, 9.18f, 10.06f, 9.33f)
            curveTo(9.93f, 9.48f, 9.79f, 9.67f, 9.67f, 9.79f)
            curveTo(9.54f, 9.92f, 9.41f, 10.06f, 9.55f, 10.31f)
            curveTo(9.69f, 10.56f, 10.27f, 11.5f, 11.09f, 12.24f)
            curveTo(12.13f, 13.18f, 12.98f, 13.48f, 13.26f, 13.6f)
            curveTo(13.47f, 13.69f, 13.72f, 13.67f, 13.87f, 13.5f)
            curveTo(14.06f, 13.29f, 14.31f, 12.95f, 14.56f, 12.69f)
            curveTo(14.74f, 12.5f, 14.97f, 12.46f, 15.2f, 12.55f)
            curveTo(15.43f, 12.64f, 16.5f, 13.19f, 16.71f, 13.3f)
            curveTo(16.92f, 13.41f, 17.06f, 13.46f, 17.1f, 13.55f)
            curveTo(17.14f, 13.64f, 17.14f, 14.05f, 16.97f, 14.54f)
            close()
        }
    }.build()
}
