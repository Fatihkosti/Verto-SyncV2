package com.verto.app.feature.integration.optimal.domain.port

import com.verto.app.feature.integration.optimal.domain.model.OptimalAttachmentSelection
import com.verto.app.feature.integration.optimal.domain.model.OptimalCameraCaptureTarget
import com.verto.app.feature.integration.optimal.domain.model.OptimalAudioRecordingTarget
import com.verto.app.feature.integration.optimal.domain.model.OptimalStoredAttachment

/** Imports bytes into app-private, tenant/user-scoped storage. */
interface OptimalAttachmentStore {
    suspend fun prepareCameraTarget(
        organizationId: String,
        userId: String,
        mediaId: String,
    ): Result<OptimalCameraCaptureTarget>


    suspend fun prepareAudioTarget(
        organizationId: String,
        userId: String,
        mediaId: String,
    ): Result<OptimalAudioRecordingTarget>

    suspend fun finalizeAudioTarget(
        organizationId: String,
        userId: String,
        target: OptimalAudioRecordingTarget,
        elapsedDurationMs: Long,
    ): Result<OptimalStoredAttachment>

    suspend fun discardAudioTarget(
        organizationId: String,
        userId: String,
        target: OptimalAudioRecordingTarget,
    )

    suspend fun importAttachment(
        organizationId: String,
        userId: String,
        mediaId: String,
        selection: OptimalAttachmentSelection,
    ): Result<OptimalStoredAttachment>

    suspend fun discardCameraTarget(
        organizationId: String,
        userId: String,
        target: OptimalCameraCaptureTarget,
    )

    suspend fun deleteStoredAttachment(
        organizationId: String,
        userId: String,
        attachment: OptimalStoredAttachment,
    )

    fun owns(
        organizationId: String,
        userId: String,
        privateUri: String,
    ): Boolean
}
