package com.verto.app.feature.inventory.presentation.inventory

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.app.feature.inventory.application.model.InventoryItemView
import com.verto.app.ui.theme.*
import java.util.UUID

@Composable
fun AddEditItemScreen(
    itemId: String? = null,
    onBack: () -> Unit,
    vm: InventoryViewModel = hiltViewModel()
) {
    val allItems by vm.items.collectAsStateWithLifecycle()
    val allUnits by vm.allUnits.collectAsStateWithLifecycle()
    val categoryMap by vm.itemCategoriesMap.collectAsStateWithLifecycle()
    val masterCategories by vm.allMasterCategories.collectAsStateWithLifecycle()
    val duplicateNameError by vm.duplicateNameError.collectAsStateWithLifecycle()
    val permissions by vm.permissions.collectAsStateWithLifecycle()

    AddEditItemContent(
        state = AddEditItemUiState(
            itemId = itemId,
            items = allItems,
            units = allUnits,
            categoryMap = categoryMap,
            masterCategories = masterCategories,
            duplicateNameError = duplicateNameError,
            // The route is gated before this screen is shown; initial null stays enabled.
            canEditPrice = permissions?.inventoryPrice != false,
        ),
        events = AddEditItemEvents(
            onBack = onBack,
            onClearDuplicateNameError = vm::clearDuplicateNameError,
            onSaveAsUnitItem = { item, unitName, unitQuantity, linkedPieceItemId, categories ->
                vm.saveAsUnitItem(item, unitName, unitQuantity, linkedPieceItemId, categories)
            },
            onSaveItemWithDetails = { item, categories, onSuccess ->
                vm.saveItemWithDetails(item, categories, null, onSuccess)
            },
        ),
    )
}
