package com.verto.app.feature.shipment.data.activityevent

import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import com.verto.app.data.local.AppDatabase
import com.verto.app.feature.shipment.application.activityevent.ShipmentActivityEventSource
import com.verto.app.feature.shipment.application.activityevent.ShipmentActivityRecord
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomShipmentActivityEventSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
) : ShipmentActivityEventSource {
    override fun observeSince(
        organizationId: String,
        sinceEpochMillis: Long,
        limit: Int,
    ): Flow<List<ShipmentActivityRecord>> =
        database.logisticsDao().observeActivityShipments(organizationId, sinceEpochMillis, limit).map { rows ->
            rows.map { row ->
                ShipmentActivityRecord(
                    id = row.shipmentId,
                    title = row.shipmentNumber.takeIf(String::isNotBlank)?.let {
                        context.getString(com.verto.app.feature.shipment.R.string.shipment_v298_b41f4ef156cc_2, it)
                    } ?: context.getString(com.verto.app.feature.shipment.R.string.shipment_v298_b41f4ef156cc),
                    shipmentNumber = row.shipmentNumber,
                    origin = row.sourceLocation,
                    destination = row.destinationLocation,
                    createdAtEpochMillis = row.createdAt,
                )
            }
        }
}
