package io.github.jabrena.juno.usecases.supabase.dashboard;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;
import io.github.jabrena.juno.api.mvc.ViewRouter;
import io.github.jabrena.juno.api.tft.TftTouchShield;
import io.github.jabrena.juno.usecases.supabase.commons.SupabaseClient;
import io.github.jabrena.juno.usecases.supabase.commons.SupabaseResponse;
import io.github.jabrena.juno.usecases.supabase.dashboard.commons.TemperatureDashboardState;
import io.github.jabrena.juno.usecases.supabase.dashboard.gathering.TemperatureGatheringView;
import io.github.jabrena.juno.usecases.supabase.dashboard.intro.IntroView;
import io.github.jabrena.juno.usecases.supabase.dashboard.services.SupabaseTemperatureService;
import io.github.jabrena.juno.usecases.supabase.dashboard.status.TemperatureHistogramView;
import io.github.jabrena.juno.usecases.supabase.publisher.SupabaseGroveTemperatures;

/**
 * Reads every row of the {@code sensor-data} table written by {@link SupabaseGroveTemperatures}, calculates the median
 * of each row's three temperatures, and presents one histogram on the TFT and serial monitor.
 *
 * <ul>
 *   <li><b>Model</b>: {@link TemperatureDashboardState} holds loading progress and histogram data.</li>
 *   <li><b>Views</b>: the gathering view reports bounded-page progress before {@link TemperatureHistogramView}
 *       presents the completed model.</li>
 *   <li><b>Controller</b>: the API's {@link ViewRouter} owns the view and advances background work every frame.</li>
 *   <li><b>Service</b>: {@link SupabaseTemperatureService} implements the API's background-task contract, obtains the
 *       exact count with the first page, then reads pairs of bounded pages concurrently.</li>
 * </ul>
 *
 * <p>This class only wires those pieces together. Build-time environment variables provide
 * {@code JUNO_WIFI_SSID}, {@code JUNO_WIFI_PASSWORD}, {@code SUPABASE_API_HOST} (without {@code https://}), and
 * {@code SUPABASE_API_KEY}. The table needs a Row Level Security policy granting the public client {@code SELECT}
 * access. Never embed a secret or legacy {@code service_role} key in firmware.
 *
 * <p>Each bin is one degree Celsius from 10 to 39; readings outside that range are counted in open-ended buckets. A
 * row containing any null sensor reading is counted as missing. Buffers and application objects are allocated once at
 * startup; each gathering step creates one bounded structured scope for two independent HTTPS requests,
 * and subsequent controller iterations allocate nothing. Each response buffer holds one page, so this targets UNO R4
 * WiFi and UNO Q. On the R4, build with {@code -Djuno.Xmx=9800 -Djuno.Xss=2k -Djuno.maxThreads=3} so its two task
 * stacks fit beside the WiFi and TFT runtimes.
 */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})
public final class SupabaseGroveTemperaturesHistogram {
    private static final int INTRO_VIEW = 0;
    private static final int GATHERING_VIEW = 1;
    private static final int DASHBOARD_VIEW = 2;
    private static final int FRAME_PERIOD_MILLIS = 100;
    private static final int PATH_CAPACITY = 192;
    private static final int REQUEST_HEADERS_CAPACITY = 144;
    private static final int REQUEST_BODY_CAPACITY = 1;
    private static final int RESPONSE_BODY_CAPACITY = 3584;
    private static final int RESPONSE_HEADERS_CAPACITY = 256;

    private SupabaseGroveTemperaturesHistogram() {
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(1000);
        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.PORTRAIT_FLIPPED);

        byte[] path = new byte[PATH_CAPACITY];
        byte[] requestHeaders = new byte[REQUEST_HEADERS_CAPACITY];
        byte[] requestBody = new byte[REQUEST_BODY_CAPACITY];
        byte[] responseBody = new byte[RESPONSE_BODY_CAPACITY];
        byte[] responseHeaders = new byte[RESPONSE_HEADERS_CAPACITY];
        int[] status = new int[2];
        SupabaseResponse response = new SupabaseResponse(
                responseBody, RESPONSE_BODY_CAPACITY,
                responseHeaders, RESPONSE_HEADERS_CAPACITY,
                status);
        SupabaseClient supabase = new SupabaseClient(
                System.getenv("SUPABASE_API_HOST"),
                System.getenv("SUPABASE_API_KEY"),
                path, PATH_CAPACITY,
                requestHeaders, REQUEST_HEADERS_CAPACITY,
                requestBody, REQUEST_BODY_CAPACITY,
                response);

        byte[] nextPath = new byte[PATH_CAPACITY];
        byte[] nextRequestHeaders = new byte[REQUEST_HEADERS_CAPACITY];
        byte[] nextRequestBody = new byte[REQUEST_BODY_CAPACITY];
        byte[] nextResponseBody = new byte[RESPONSE_BODY_CAPACITY];
        byte[] nextResponseHeaders = new byte[RESPONSE_HEADERS_CAPACITY];
        int[] nextStatus = new int[2];
        SupabaseResponse nextResponse = new SupabaseResponse(
                nextResponseBody, RESPONSE_BODY_CAPACITY,
                nextResponseHeaders, RESPONSE_HEADERS_CAPACITY,
                nextStatus);
        SupabaseClient nextSupabase = new SupabaseClient(
                System.getenv("SUPABASE_API_HOST"),
                System.getenv("SUPABASE_API_KEY"),
                nextPath, PATH_CAPACITY,
                nextRequestHeaders, REQUEST_HEADERS_CAPACITY,
                nextRequestBody, REQUEST_BODY_CAPACITY,
                nextResponse);

        TemperatureDashboardState model = new TemperatureDashboardState();
        SupabaseTemperatureService service = new SupabaseTemperatureService(
                supabase, response, RESPONSE_BODY_CAPACITY,
                nextSupabase, nextResponse,
                model);
        ViewRouter controller = new ViewRouter(FRAME_PERIOD_MILLIS);
        controller.register(INTRO_VIEW,
                new IntroView(controller, GATHERING_VIEW));
        controller.register(GATHERING_VIEW, new TemperatureGatheringView(model, service, controller, DASHBOARD_VIEW));
        controller.register(DASHBOARD_VIEW, new TemperatureHistogramView(model));
        controller.start(INTRO_VIEW);

        controller.background(service);
        controller.run();
    }
}
