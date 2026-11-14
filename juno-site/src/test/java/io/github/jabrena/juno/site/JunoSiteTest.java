package io.github.jabrena.juno.site;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

@QuarkusTest
class JunoSiteTest {

    @Test
    void homeRendersHero() {
        RestAssured.given().when().get("/").then().statusCode(200)
                .body(containsString("Java, compiled straight to the board"))
                .body(containsString("Get started"));
    }

    @Test
    void homeUsesJunoPhotoAsHeroBackground() {
        RestAssured.given().when().get("/").then().statusCode(200)
                .body(containsString("juno-hero"))
                .body(containsString("images/juno.png"));
    }

    @Test
    void docsPagesHaveOnlyTheTopNavigation() {
        RestAssured.given().when().get("/hardware-apis/").then().statusCode(200)
                .body(containsString("juno-topbar"))
                .body(containsString("juno-footer"))
                .body(not(containsString("sidebar main")));
    }

    @Test
    void homeHasNoSecondaryNavigation() {
        RestAssured.given().when().get("/").then().statusCode(200)
                .body(not(containsString("juno-subnav")));
    }

    @Test
    void featuresSectionListsItsPagesInSecondaryNavigation() {
        RestAssured.given().when().get("/memory-model/").then().statusCode(200)
                .body(containsString("juno-subnav"))
                .body(containsString("JLS 17 Conformance"))
                .body(containsString("Java subset"));
    }

    @Test
    void gettingStartedPageRenders() {
        RestAssured.given().when().get("/getting-started/").then().statusCode(200)
                .body(containsString("Explore the project"));
    }

    @Test
    void boardFeaturesPageRendersInFeaturesSection() {
        RestAssured.given().when().get("/board-features/").then().statusCode(200)
                .body(containsString("Infrared link to LEGO RCX"))
                .body(containsString("juno-subnav"));
    }

    @Test
    void javaSubsetPageRendersInFeaturesSection() {
        RestAssured.given().when().get("/java-subset/").then().statusCode(200)
                .body(containsString("Program entry points"))
                .body(containsString("juno-subnav"));
    }

    @Test
    void featuresPageRenders() {
        RestAssured.given().when().get("/features/").then().statusCode(200);
    }

    @Test
    void mavenPluginPagePreservesLiteralMavenProperties() {
        RestAssured.given().when().get("/juno-maven-plugin/").then().statusCode(200)
                .body(containsString("${project.version}"));
    }

    @Test
    void boardsSectionListsBoardsInSecondaryNavigation() {
        RestAssured.given().when().get("/arduino-uno-q/").then().statusCode(200)
                .body(containsString("juno-subnav"))
                .body(containsString("UNO R4 WiFi"))
                .body(containsString("Nano ESP32"))
                .body(not(containsString("Clock &amp; Delay")));
    }

    @Test
    void boardsOverviewComparesEveryBoard() {
        RestAssured.given().when().get("/boards/").then().statusCode(200)
                .body(containsString("Hardware at a glance"))
                .body(containsString("arduino-uno-r3-wifi-rev2"))
                .body(containsString("m5stack-cardputer"))
                .body(containsString("juno-subnav"));
    }

    @Test
    void createYourFirstProjectPageExplainsSubmoduleAndPlugin() {
        RestAssured.given().when().get("/create-your-first-project/").then().statusCode(200)
                .body(containsString("git submodule add"))
                .body(containsString("mainClass"))
                .body(containsString("juno:flash"));
    }

    @Test
    void thirdPartiesSectionListsShieldsGroveAndLego() {
        RestAssured.given().when().get("/third-parties/").then().statusCode(200)
                .body(containsString("juno-subnav"))
                .body(containsString("Arduino Shields"))
                .body(containsString("/grove-system"))
                .body(containsString("/lego-ecosystem"));
    }

    @Test
    void shieldsPageListsEveryShield() {
        RestAssured.given().when().get("/arduino-shields/").then().statusCode(200)
                .body(containsString("TFT Touch Shield"))
                .body(containsString("Nano Connector Carrier"));
    }

    @Test
    void apisSectionListsHardwareApisInSecondaryNavigation() {
        RestAssured.given().when().get("/hardware-apis/").then().statusCode(200)
                .body(containsString("juno-subnav"))
                .body(containsString("Telemetry"))
                .body(containsString("Storage"))
                .body(containsString("Internet"))
                .body(containsString("Utils"))
                .body(containsString("Hardware"))
                .body(containsString("href=\"/hardware-apis#telemetry\""))
                .body(containsString("href=\"/hardware-apis#storage\""))
                .body(containsString("juno-topnav-children"));
    }

    @Test
    void timePageRendersInApisSection() {
        RestAssured.given().when().get("/time/").then().statusCode(200)
                .body(containsString("LocalDateTime.now()"))
                .body(containsString("juno-subnav"));
    }

    @Test
    void mvcPageRendersInApisSection() {
        RestAssured.given().when().get("/mvc/").then().statusCode(200)
                .body(containsString("ViewRouter"))
                .body(containsString("juno-subnav"));
    }

    @Test
    void gpioPageShowsBlink() {
        RestAssured.given().when().get("/gpio/").then().statusCode(200)
                .body(containsString("public class Blink"))
                .body(containsString("juno-subnav"));
    }

    @Test
    void homeHasHamburgerMenuForMobileNavigation() {
        RestAssured.given().when().get("/").then().statusCode(200)
                .body(containsString("juno-menu-toggle"))
                .body(containsString("aria-controls=\"juno-topnav\""));
    }

    @Test
    void homeSecondCarouselIncludesNewHardwareImages() {
        RestAssured.given().when().get("/").then().statusCode(200)
                .body(containsString("images/grove/grove-bmi088.jpg"))
                .body(containsString("images/grove/grove-ultrasonic-ranger.png"))
                .body(containsString("images/grove/grove-oled-ssd1315.jpg"))
                .body(containsString("images/motors/l298n-driver.png"))
                .body(containsString("images/motors/tt-gear-motor.jpeg"))
                .body(containsString("BMI088 IMU"))
                .body(containsString("L298N driver"))
                .body(containsString("TT gear motor"));
    }

    @Test
    void showcaseIsInMainNavigation() {
        RestAssured.given().when().get("/").then().statusCode(200)
                .body(containsString("Showcase"));
    }

    @Test
    void showcasePageListsArticlesAndPagination() {
        RestAssured.given().when().get("/showcase/").then().statusCode(200)
                .body(containsString("juno-subnav"))
                .body(containsString("/blog/gladiator/"))
                .body(containsString("/blog/lichess/"))
                .body(containsString("/blog/mailbox/"))
                .body(containsString("/posts/page2/"));
    }

    @Test
    void showcaseArticleRendersDetail() {
        RestAssured.given().when().get("/blog/mailbox/").then().statusCode(200)
                .body(containsString("Mailbox is a remote messaging system for Claude"))
                .body(containsString("usecases/mailbox"));
    }

    @Test
    void showcasePaginationRendersNextPage() {
        RestAssured.given().when().get("/posts/page2/").then().statusCode(200)
                .body(containsString("Supabase: publish Grove temperatures to the cloud"))
                .body(containsString("Showcase"));
    }

}
