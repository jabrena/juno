package io.github.jabrena.juno.remote.lego.poweredup;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Powered Up hub controller over the BLE radio")
class RemotePoweredUpHubRadioTest {
    @Test
    @DisplayName("Connects to a City Hub through the radio it is given")
    void shouldConnectThroughTheRadioItIsGiven() {
        // Given
        FakeBluetoothLe radio = new FakeBluetoothLe();
        RemotePoweredUpHub hub = new RemotePoweredUpHub(radio, new FakeClock());

        // When
        boolean connected = hub.connect(0);

        // Then
        assertThat(connected).isTrue();
        assertThat(hub.hubType()).isEqualTo(RemotePoweredUpHub.TYPE_CITY_HUB);
        assertThat(radio.connects()).isEqualTo(1);
    }
}
