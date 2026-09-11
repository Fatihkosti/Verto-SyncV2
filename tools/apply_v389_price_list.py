from pathlib import Path

SCREEN = Path('feature/inventory/src/main/kotlin/com/verto/app/feature/inventory/presentation/pricelist/PriceListScreen.kt')
PDF = Path('feature/inventory/src/main/kotlin/com/verto/app/pdf/PriceListPdf.kt')
STRINGS = Path('feature/inventory/src/main/res/values/strings.xml')


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 match, got {count}')
    return text.replace(old, new, 1)


s = SCREEN.read_text()
s = replace_once(
    s,
    '    var showDate by remember { mutableStateOf(true) }\n',
    '    var showDate by remember { mutableStateOf(true) }\n    var showPrices by remember { mutableStateOf(true) }\n',
    'screen showPrices state',
)

date_block = '''            if (draftItems.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(BgCard)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(stringResource(com.verto.feature.inventory.R.string.price_list_show_date), color = TextPrimary)
                        Switch(checked = showDate, onCheckedChange = { showDate = it })
                    }
                }
            }
'''
options_block = '''            if (draftItems.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(BgCard)
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(stringResource(com.verto.feature.inventory.R.string.price_list_show_date), color = TextPrimary)
                            Switch(checked = showDate, onCheckedChange = { showDate = it })
                        }
                        HorizontalDivider(color = BorderColor)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(stringResource(com.verto.feature.inventory.R.string.price_list_show_prices), color = TextPrimary)
                            Switch(checked = showPrices, onCheckedChange = { showPrices = it })
                        }
                    }
                }
            }
'''
s = replace_once(s, date_block, options_block, 'screen options block')
s = replace_once(
    s,
    '                                        dateString = date,\n                                        font = priceListFont,\n',
    '                                        dateString = date,\n                                        includePrices = showPrices,\n                                        font = priceListFont,\n',
    'screen PDF includePrices',
)
SCREEN.write_text(s)

p = PDF.read_text()
old_columns = '''private val PRICE_COLUMNS = listOf(
    "#" to 35f,
    "الصنف" to 275f,
    "رقم القطعة" to 105f,
    "السعر" to 100f,
)
'''
new_columns = '''private val PRICE_COLUMNS_WITH_PRICE = listOf(
    "#" to 35f,
    "الصنف" to 275f,
    "رقم القطعة" to 105f,
    "السعر" to 100f,
)
private val PRICE_COLUMNS_WITHOUT_PRICE = listOf(
    "#" to 35f,
    "الصنف" to 375f,
    "رقم القطعة" to 105f,
)
'''
p = replace_once(p, old_columns, new_columns, 'pdf columns')
p = replace_once(
    p,
    '    dateString: String? = null,\n    font: InvoiceFont = InvoiceFont.CAIRO,\n',
    '    dateString: String? = null,\n    includePrices: Boolean = true,\n    font: InvoiceFont = InvoiceFont.CAIRO,\n',
    'pdf includePrices parameter',
)
p = replace_once(
    p,
    '    val columns = rtlCols(PRICE_COLUMNS)\n',
    '    val columns = rtlCols(if (includePrices) PRICE_COLUMNS_WITH_PRICE else PRICE_COLUMNS_WITHOUT_PRICE)\n',
    'pdf dynamic columns',
)
p = replace_once(
    p,
    '        cellRight(canvas, AmountFormatter.format(item.price), columns[3].s, columns[3].e, y, TABLE_ROW_HEIGHT, pricePaint)\n',
    '        if (includePrices) {\n            cellRight(canvas, AmountFormatter.format(item.price), columns[3].s, columns[3].e, y, TABLE_ROW_HEIGHT, pricePaint)\n        }\n',
    'pdf conditional price cell',
)
PDF.write_text(p)

x = STRINGS.read_text()
x = replace_once(
    x,
    '    <string name="price_list_show_date" translatable="false">إظهار التاريخ</string>\n',
    '    <string name="price_list_show_date" translatable="false">إظهار التاريخ</string>\n    <string name="price_list_show_prices" translatable="false">إظهار الأسعار</string>\n',
    'price-list show-prices string',
)
STRINGS.write_text(x)
