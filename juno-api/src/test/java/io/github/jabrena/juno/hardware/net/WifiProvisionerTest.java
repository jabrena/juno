package io.github.jabrena.juno.hardware.net;

import io.github.jabrena.juno.hardware.net.http.HttpMethod;
import io.github.jabrena.juno.hardware.net.http.HttpServer;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.testing.SimulatedTime;
import io.github.jabrena.juno.testing.Wire;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Wi-Fi provisioning access point")
class WifiProvisionerTest {
    private static final String FORM_BODY = "ssid=Home+Net&password=p%40ss";

    private FakeWifi wifi = new FakeWifi();

    @BeforeEach
    void startWithAnIdleBoard() {
        Wire.resetBoard();
        WifiProvisioner.clock = SimulatedTime.clock();
        wifi = new FakeWifi();
        WifiProvisioner.wifi = wifi;
        HttpServer.reset();
        Serial.reset();
    }

    @Test
    @DisplayName("Serves the setup form on port 80")
    void shouldListenOnPort80() {
        // Given
        wifi.joinOutcomes.add(true);
        script(idle(), get("/"), post("/connect", FORM_BODY));

        // When
        WifiProvisioner.run("Juno-Setup", "juno1234");

        // Then
        assertThat(HttpServer.port).isEqualTo(80);
    }

    @Test
    @DisplayName("Serves the Wi-Fi setup form at the root path")
    void shouldServeTheSetupFormAtTheRootPath() {
        // Given
        wifi.joinOutcomes.add(true);
        script(idle(), get("/"), post("/connect", FORM_BODY));

        // When
        WifiProvisioner.run("Juno-Setup", "juno1234");

        // Then
        assertThat(HttpServer.responses.get(0).body()).contains("Wi-Fi setup");
    }

    @Test
    @DisplayName("Answers the connect request while joining the network")
    void shouldAnswerTheConnectRequestWhileJoiningTheNetwork() {
        // Given
        wifi.joinOutcomes.add(true);
        script(idle(), get("/"), post("/connect", FORM_BODY));

        // When
        WifiProvisioner.run("Juno-Setup", "juno1234");

        // Then
        assertThat(HttpServer.responses.get(1).body()).contains("Connecting...");
    }

    @Test
    @DisplayName("Joins the network with the decoded credentials")
    void shouldJoinTheNetworkWithTheDecodedCredentials() {
        // Given
        wifi.joinOutcomes.add(true);
        script(idle(), get("/"), post("/connect", FORM_BODY));

        // When
        WifiProvisioner.run("Juno-Setup", "juno1234");

        // Then
        assertThat(wifi.joins).containsExactly("Home Net|p@ss");
    }

    @Test
    @DisplayName("Opens the setup access point with its name")
    void shouldOpenTheSetupAccessPointWithItsName() {
        // Given
        wifi.joinOutcomes.add(true);
        script(idle(), get("/"), post("/connect", FORM_BODY));

        // When
        WifiProvisioner.run("Juno-Setup", "juno1234");

        // Then
        assertThat(wifi.accessPointSsid).isEqualTo("Juno-Setup");
    }

    @Test
    @DisplayName("Logs each provisioning step to serial")
    void shouldLogEachProvisioningStepToSerial() {
        // Given
        wifi.joinOutcomes.add(true);
        script(idle(), get("/"), post("/connect", FORM_BODY));

        // When
        WifiProvisioner.run("Juno-Setup", "juno1234");

        // Then
        assertThat(Serial.output).containsSubsequence("Access point ready: ", "Juno-Setup\n",
                "Open http://", "192", ".", "168", ".", "4", ".", "1", "/\n",
                "Credentials received for: ", "Home Net\n",
                "Connected. IP: ", "192", ".", "168", ".", "1", ".", "42", "\n");
    }

    @Test
    @DisplayName("Answers unknown paths with a not-found status")
    void shouldAnswerUnknownPathsWithANotFoundStatus() {
        // Given
        wifi.joinOutcomes.add(true);
        script(get("/missing"), post("/connect", FORM_BODY));

        // When
        WifiProvisioner.run("Juno-Setup", "juno1234");

        // Then
        assertThat(HttpServer.responses.get(0).status()).isEqualTo(404);
    }

    @Test
    @DisplayName("Answers unknown paths with a Not found body")
    void shouldAnswerUnknownPathsWithANotFoundBody() {
        // Given
        wifi.joinOutcomes.add(true);
        script(get("/missing"), post("/connect", FORM_BODY));

        // When
        WifiProvisioner.run("Juno-Setup", "juno1234");

        // Then
        assertThat(HttpServer.responses.get(0).body()).isEqualTo("Not found");
    }

    @Test
    @DisplayName("Tries the join again after a failed attempt")
    void shouldTryTheJoinAgainAfterAFailedAttempt() {
        // Given
        wifi.joinOutcomes.add(false);
        wifi.joinOutcomes.add(true);
        script(post("/connect", FORM_BODY), post("/connect", FORM_BODY));

        // When
        WifiProvisioner.run("Juno-Setup", "juno1234");

        // Then
        assertThat(wifi.joins).containsExactly("Home Net|p@ss", "Home Net|p@ss");
    }

    @Test
    @DisplayName("Reopens the setup network and reports a failed join")
    void shouldReopenTheSetupNetworkAndReportAFailedJoin() {
        // Given
        wifi.joinOutcomes.add(false);
        wifi.joinOutcomes.add(true);
        script(post("/connect", FORM_BODY), post("/connect", FORM_BODY));

        // When
        WifiProvisioner.run("Juno-Setup", "juno1234");

        // Then
        assertThat(Serial.output).contains("Connection failed, reopening the setup network\n");
    }

    @Test
    @DisplayName("Does not join when the board is already on the network")
    void shouldNotJoinWhenTheBoardIsAlreadyOnTheNetwork() {
        // Given
        wifi.connectedWhileAccessPoint = true;
        script(post("/connect", "ssid=Office&password=%4Aa%4b"));

        // When
        WifiProvisioner.run("Juno-Setup", "juno1234");

        // Then
        assertThat(wifi.joins).as("no second join is needed").isEmpty();
    }

    @Test
    @DisplayName("Prints the command to join when the board is already on the network")
    void shouldPrintTheCommandToJoinWhenTheBoardIsAlreadyOnTheNetwork() {
        // Given
        wifi.connectedWhileAccessPoint = true;
        script(post("/connect", "ssid=Office&password=%4Aa%4b"));

        // When
        WifiProvisioner.run("Juno-Setup", "juno1234");

        // Then
        assertThat(Serial.output).containsSubsequence(
                "Apply on Linux: nmcli device wifi connect ", "Office", " password ", "JaK\n");
    }

    @Test
    @DisplayName("Waits for the access point to come up")
    void shouldWaitForTheAccessPointToComeUp() {
        // Given
        wifi.warmupReads = 2;
        wifi.joinOutcomes.add(true);
        script(post("/connect", FORM_BODY));

        // When
        WifiProvisioner.run("Juno-Setup", "juno1234");

        // Then
        assertThat(SimulatedTime.now).as("one 200 ms wait: the loop reads the status twice per pass, so both warm-up reads take one pass")
                .isEqualTo(200_000);
    }

    @Test
    @DisplayName("Keeps a percent sign that is not followed by two digits and truncates long names")
    void shouldKeepAStrayPercentAndTruncateLongNamesWhenJoining() {
        // Given
        wifi.joinOutcomes.add(true);
        String longName = "x".repeat(40);
        script(post("/connect", "ssid=" + longName + "&password=p%4"));

        // When
        WifiProvisioner.run("Juno-Setup", "juno1234");

        // Then
        assertThat(wifi.joins).containsExactly("x".repeat(31) + "|p%4");
    }

    @Test
    @DisplayName("Logs the truncated network name")
    void shouldLogTheTruncatedNetworkName() {
        // Given
        wifi.joinOutcomes.add(true);
        String longName = "x".repeat(40);
        script(post("/connect", "ssid=" + longName + "&password=p%4"));

        // When
        WifiProvisioner.run("Juno-Setup", "juno1234");

        // Then
        assertThat(Serial.output).contains("x".repeat(31) + "\n");
    }

    @Test
    @DisplayName("An invalid hex escape decodes to NUL")
    void shouldDecodeAnInvalidHexEscapeToNul() {
        // Given
        wifi.joinOutcomes.add(true);
        script(post("/connect", "ssid=A&password=%zz"));

        // When
        WifiProvisioner.run("Juno-Setup", "juno1234");

        // Then
        assertThat(wifi.joins).containsExactly("A|\u0000");
    }

    @Test
    @DisplayName("A body without fields yields empty credentials")
    void shouldYieldEmptyCredentialsForABodyWithoutFields() {
        // Given
        wifi.joinOutcomes.add(true);
        script(post("/connect", "garbage"));

        // When
        WifiProvisioner.run("Juno-Setup", "juno1234");

        // Then
        assertThat(wifi.joins).containsExactly("|");
    }

    /** Scripts the requests the server will receive, in order. */
    private static void script(HttpServer.Request... requests) {
        for (HttpServer.Request request : requests) {
            HttpServer.requests.add(request);
        }
    }

    private static HttpServer.Request idle() {
        return new HttpServer.Request(null, null, null);
    }

    private static HttpServer.Request get(String path) {
        return new HttpServer.Request(HttpMethod.GET, path, "");
    }

    private static HttpServer.Request post(String path, String body) {
        return new HttpServer.Request(HttpMethod.POST, path, body);
    }
}
