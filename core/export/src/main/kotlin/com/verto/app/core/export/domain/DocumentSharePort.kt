package com.verto.app.core.export.domain

import java.io.File

/** مشاركة مستند جاهز؛ لا يملك مسؤولية توليد المحتوى. */
interface DocumentSharePort {
    fun sharePdf(file: File)
}
