package com.verto.app.feature.sync.di

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConflictResolutionPermissionPolicyB11Test {
    @Test fun `admin is authorized`() = assertTrue(ConflictResolutionPermissionPolicy.allowed("admin", false))
    @Test fun `employee with org-data permission is authorized`() = assertTrue(ConflictResolutionPermissionPolicy.allowed("employee", true))
    @Test fun `employee without org-data permission is rejected`() = assertFalse(ConflictResolutionPermissionPolicy.allowed("employee", false))
}
