package io.github.jabrena.juno.maven;

import org.codehaus.plexus.configuration.DefaultPlexusConfiguration;
import org.codehaus.plexus.configuration.PlexusConfiguration;

/** Builds a Mojo's {@code <configuration>} in pure Java, for {@code AbstractMojoTestCase#configureMojo}, instead
 * of a {@code plugin-config.xml} fixture file. */
final class MojoConfig {
    private final PlexusConfiguration root = new DefaultPlexusConfiguration("configuration");

    static MojoConfig configuration() {
        return new MojoConfig();
    }

    MojoConfig set(String parameterName, String value) {
        root.addChild(parameterName, value);
        return this;
    }

    MojoConfig list(String parameterName, String elementName, String... values) {
        PlexusConfiguration list = new DefaultPlexusConfiguration(parameterName);
        for (String value : values) {
            list.addChild(elementName, value);
        }
        root.addChild(list);
        return this;
    }

    PlexusConfiguration build() {
        return root;
    }
}
