package io.github.jabrena.juno.api.net;

import io.github.jabrena.juno.api.io.serial.Serial;
import io.github.jabrena.juno.api.net.http.HttpMethod;
import io.github.jabrena.juno.api.net.http.HttpServer;
import io.github.jabrena.juno.api.testing.Wire;
import io.github.jabrena.juno.api.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WifiProvisionerTest {
    private static final String FORM_BODY = "ssid=Home+Net&password=p%40ss";

    @BeforeEach
    void startWithAnIdleBoard() {
        Wire.resetBoard();
        Wifi.reset();
        HttpServer.reset();
        Serial.reset();
    }

    @Test
    void servesTheFormAndProvisionsTheNetworkItIsGiven() {
        Wifi.joinOutcomes.add(true);
        script(idle(), get("/"), post("/connect", FORM_BODY));

        WifiProvisioner.run("Juno-Setup", "juno1234");

        assertThat(HttpServer.port).isEqualTo(80);
        assertThat(HttpServer.responses.get(0).body()).contains("Wi-Fi setup");
        assertThat(HttpServer.responses.get(1).body()).contains("Connecting...");
        assertThat(Wifi.joins).containsExactly("Home Net|p@ss");
        assertThat(Wifi.accessPointSsid).isEqualTo("Juno-Setup");
        assertThat(Serial.output).containsSubsequence("Access point ready: ", "Juno-Setup\n",
                "Credentials received for: ", "Home Net\n",
                "Connected. IP: ", "192", ".", "168", ".", "1", ".", "42\n");
    }

    @Test
    void answersUnknownPathsWithNotFound() {
        Wifi.joinOutcomes.add(true);
        script(get("/missing"), post("/connect", FORM_BODY));

        WifiProvisioner.run("Juno-Setup", "juno1234");

        assertThat(HttpServer.responses.get(0).status()).isEqualTo(404);
        assertThat(HttpServer.responses.get(0).body()).isEqualTo("Not found");
    }

    @Test
    void reopensTheSetupNetworkWhenTheJoinFailsAndTriesAgain() {
        Wifi.joinOutcomes.add(false);
        Wifi.joinOutcomes.add(true);
        script(post("/connect", FORM_BODY), post("/connect", FORM_BODY));

        WifiProvisioner.run("Juno-Setup", "juno1234");

        assertThat(Wifi.joins).containsExactly("Home Net|p@ss", "Home Net|p@ss");
        assertThat(Serial.output).contains("Connection failed, reopening the setup network\n");
    }

    @Test
    void reportsTheCommandToJoinWhenTheBoardIsAlreadyOnTheNetwork() {
        Wifi.connectedWhileAccessPoint = true;
        script(post("/connect", "ssid=Office&password=%4Aa%4b"));

        WifiProvisioner.run("Juno-Setup", "juno1234");

        assertThat(Wifi.joins).as("no second join is needed").isEmpty();
        assertThat(Serial.output).containsSubsequence(
                "Apply on Linux: nmcli device wifi connect ", "Office", " password ", "JaK\n");
    }

    @Test
    void waitsForTheAccessPointToComeUp() {
        Wifi.warmupReads = 2;
        Wifi.joinOutcomes.add(true);
        script(post("/connect", FORM_BODY));

        WifiProvisioner.run("Juno-Setup", "juno1234");

        assertThat(Clock.now).as("one 200 ms wait: the loop reads the status twice per pass, so both warm-up reads take one pass")
                .isEqualTo(200_000);
    }

    @Test
    void keepsAPercentThatIsNotFollowedByTwoDigitsAndTruncatesLongNames() {
        Wifi.joinOutcomes.add(true);
        String longName = "x".repeat(40);
        script(post("/connect", "ssid=" + longName + "&password=p%4"));

        WifiProvisioner.run("Juno-Setup", "juno1234");

        assertThat(Wifi.joins).containsExactly("x".repeat(31) + "|p%4");
        assertThat(Serial.output).contains("x".repeat(31) + "\n");
    }

    @Test
    void anInvalidHexEscapeDecodesToNul() {
        Wifi.joinOutcomes.add(true);
        script(post("/connect", "ssid=A&password=%zz"));

        WifiProvisioner.run("Juno-Setup", "juno1234");

        assertThat(Wifi.joins).containsExactly("A|\u0000");
    }

    @Test
    void aBodyWithoutFieldsYieldsEmptyCredentials() {
        Wifi.joinOutcomes.add(true);
        script(post("/connect", "garbage"));

        WifiProvisioner.run("Juno-Setup", "juno1234");

        assertThat(Wifi.joins).containsExactly("|");
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
