package com.verto.app.utils

enum class InvoiceTemplate(val displayName: String, val description: String) {
    CLASSIC("كلاسيك", "شعار في المنتصف، تصميم تقليدي"),
    MODERN("مودرن", "شريط ملون، تصميم عصري"),
    PROFESSIONAL("بروفيشنال", "تخطيط أنيق باحترافية عالية"),
    THERMAL("حراري", "رول صغير، نص فقط، بدون صور")
}

enum class InvoiceFont(val displayName: String, val arabicName: String) {
    CAIRO("Cairo", "كايرو"),
    TAJAWAL("Tajawal", "تجوال"),
    AMIRI("Amiri", "أميري"),
    ALMARAI("Almarai", "المرعي")
}
