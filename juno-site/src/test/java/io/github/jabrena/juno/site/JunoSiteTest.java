package io.github.jabrena.juno.site;

import static org.hamcrest.Matchers.containsString;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

@QuarkusTest
class JunoSiteTest {

    @Test
    void homeRendersHero() {
        RestAssured.given().when().get("/").then().statusCode(200)
                .body(containsString("Java, compiled straight to the board"))
                .body(containsString("Get started"));
    }

    @Test
    void gettingStartedPageRenders() {
        RestAssured.given().when().get("/getting-started/").then().statusCode(200)
                .body(containsString("Quick start"));
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
    void navigationMenuListsBoardsGroup() {
        RestAssured.given().when().get("/").then().statusCode(200)
                .body(containsString("Boards"))
                .body(containsString("Hardware APIs"));
    }
}
