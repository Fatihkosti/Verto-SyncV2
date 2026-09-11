package com.verto.app.feature.management.domain.repository

import com.verto.app.feature.management.domain.model.BenzineClientError
import com.verto.app.feature.management.domain.model.BenzineJoinCodeCandidate
import com.verto.app.feature.management.domain.model.BenzineUserHealth

/**
 * AutoDrive control-plane boundary inside Verto.
 * Registration follows AutoDrive's existing invite-code flow; Verto only issues codes
 * for eligible, currently-unlinked marketer/workshop client records.
 */
interface BenzineControlPlaneGateway {
    suspend fun loadJoinCodeCandidates(): Result<List<BenzineJoinCodeCandidate>>
    suspend fun issueJoinCode(clientId: String, accountType: String): Result<String>
    suspend fun loadUserHealth(): Result<List<BenzineUserHealth>>
    suspend fun loadRecentErrors(limit: Int = 100): Result<List<BenzineClientError>>
}
