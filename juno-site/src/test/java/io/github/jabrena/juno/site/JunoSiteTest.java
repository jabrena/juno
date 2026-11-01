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
    void gamesPageEmbedsGifs() {
        RestAssured.given().when().get("/games/").then().statusCode(200)
                .body(containsString("/images/games/pacman-cpu.gif"));
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
                .body(containsString("juno:upload"));
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
        RestAssured.given().when().get("/serial/").then().statusCode(200)
                .body(containsString("juno-subnav"))
                .body(containsString("Delay"))
                .body(containsString("SD Card"))
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
    void showcaseIsInMainNavigation() {
        RestAssured.given().when().get("/").then().statusCode(200)
                .body(containsString("Showcase"));
    }

    @Test
    void showcasePageListsExamplesInSecondaryNavigation() {
        RestAssured.given().when().get("/showcase/").then().statusCode(200)
                .body(containsString("juno-subnav"))
                .body(containsString("/images/games/gladiator-solo.gif"))
                .body(containsString("/images/games/lichess-daily.gif"))
                .body(containsString("Polyphonic RCX"));
    }

}
