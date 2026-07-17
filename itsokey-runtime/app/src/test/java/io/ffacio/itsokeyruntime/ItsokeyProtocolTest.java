package io.ffacio.itsokeyruntime;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ItsokeyProtocolTest {
    @Test public void deviceIndexValidationAllowsObservedServerFormats() {
        assertTrue(ItsokeyApiClient.validDeviceIdx("12345"));
        assertTrue(ItsokeyApiClient.validDeviceIdx("device-01:main"));
        assertFalse(ItsokeyApiClient.validDeviceIdx(""));
        assertFalse(ItsokeyApiClient.validDeviceIdx("device/1"));
        assertFalse(ItsokeyApiClient.validDeviceIdx("1 2"));
        assertFalse(ItsokeyApiClient.validDeviceIdx("1\nAuthorization: Bearer x"));
    }
}
