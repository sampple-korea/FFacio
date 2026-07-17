package io.ffacio.itsokeyruntime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ItsokeyContractTest {
    @Test public void endpointsMatchObservedAppContract() {
        assertEquals("https://v2.api.itsokey.kr", ItsokeyApiClient.BASE_URL);
        assertEquals("/api/widget/oauth/generated.do", ItsokeyApiClient.GENERATE_PATH);
        assertEquals("/api/widget/devices.do", ItsokeyApiClient.DEVICES_PATH);
        assertEquals("/api/widget/device/", ItsokeyApiClient.CONTROL_PATH_PREFIX);
        assertEquals("/control.do", ItsokeyApiClient.CONTROL_PATH_SUFFIX);
        assertEquals("/api/widget/oauth/refresh.do", ItsokeyApiClient.REFRESH_PATH);
    }

    @Test public void deviceIdValidationAcceptsObservedStringIdsOnly() {
        assertTrue(ItsokeyApiClient.validDeviceIdx("12345"));
        assertTrue(ItsokeyApiClient.validDeviceIdx("lock_01-AB:2"));
        assertFalse(ItsokeyApiClient.validDeviceIdx(""));
        assertFalse(ItsokeyApiClient.validDeviceIdx("../bad"));
        assertFalse(ItsokeyApiClient.validDeviceIdx("a/b"));
    }

    @Test public void authorizationAddsExactlyOneBearerPrefix() {
        ItsokeySession raw = new ItsokeySession("Bearer", "abc", "def", 1L, 2L, "");
        assertEquals("Bearer abc", raw.accessAuthorization());
        assertEquals("Bearer def", raw.refreshAuthorization());
        ItsokeySession prefixed = new ItsokeySession("Bearer", "Bearer abc", "Bearer def", 1L, 2L, "");
        assertEquals("Bearer abc", prefixed.accessAuthorization());
        assertEquals("Bearer def", prefixed.refreshAuthorization());
    }

    @Test public void webExpiryTreatsAuthorizationValuesAsDurations() {
        long before = System.currentTimeMillis();
        long parsed = SecureSessionStore.parseWebExpiry("3600000");
        long after = System.currentTimeMillis();
        assertTrue(parsed >= before + 3_570_000L);
        assertTrue(parsed <= after + 3_570_000L);
        assertEquals(1_700_000_000_000L, SecureSessionStore.parseWebExpiry("1700000000000"));
    }
}
