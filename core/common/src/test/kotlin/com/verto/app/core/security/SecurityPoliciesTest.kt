package com.verto.app.core.security

import org.junit.Assert.*
import org.junit.Test

class SecurityPoliciesTest {
    @Test fun `tenant id is trimmed and validated`() = assertEquals("org_1", TenantIsolationPolicy.requireTenantId(" org_1 "))
    @Test(expected = SecurityPolicyViolation::class) fun `cross tenant access is rejected`() { TenantIsolationPolicy.requireSameTenant("org_a", "org_b") }
    @Test fun `admin check ignores case and surrounding spaces`() { TenantIsolationPolicy.requireAdmin(" ADMIN ") }
    @Test(expected = SecurityPolicyViolation::class) fun `self targeting is rejected`() { TenantIsolationPolicy.requireDifferentActor("u1", "u1") }
    @Test fun `redactor removes common credentials and pii`() { val out=SensitiveDataRedactor.redact("Bearer abc.DEF token=secret a@b.com +249 912 345 678"); assertFalse(out.contains("secret")); assertFalse(out.contains("a@b.com")); assertFalse(out.contains("912 345")); }
    @Test fun `pseudonymous id is stable and non revealing`() { val a=SensitiveDataRedactor.pseudonymousId("user-123"); assertEquals(a, SensitiveDataRedactor.pseudonymousId("user-123")); assertFalse(a.contains("user-123")); }
}
