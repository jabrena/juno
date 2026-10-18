package io.github.jabrena.juno.maven;

import org.apache.maven.plugin.logging.Log;

import java.util.ArrayList;
import java.util.List;

/** Records every line a Mojo logs, so goal tests can assert on it instead of capturing stdout. */
final class RecordingLog implements Log {
    private final List<String> infos = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();

    List<String> infos() {
        return List.copyOf(infos);
    }

    List<String> warnings() {
        return List.copyOf(warnings);
    }

    List<String> errors() {
        return List.copyOf(errors);
    }

    @Override
    public boolean isDebugEnabled() {
        return false;
    }

    @Override
    public void debug(CharSequence content) {
    }

    @Override
    public void debug(CharSequence content, Throwable error) {
    }

    @Override
    public void debug(Throwable error) {
    }

    @Override
    public boolean isInfoEnabled() {
        return true;
    }

    @Override
    public void info(CharSequence content) {
        infos.add(content.toString());
    }

    @Override
    public void info(CharSequence content, Throwable error) {
        infos.add(content.toString());
    }

    @Override
    public void info(Throwable error) {
    }

    @Override
    public boolean isWarnEnabled() {
        return true;
    }

    @Override
    public void warn(CharSequence content) {
        warnings.add(content.toString());
    }

    @Override
    public void warn(CharSequence content, Throwable error) {
        warnings.add(content.toString());
    }

    @Override
    public void warn(Throwable error) {
    }

    @Override
    public boolean isErrorEnabled() {
        return true;
    }

    @Override
    public void error(CharSequence content) {
        errors.add(content.toString());
    }

    @Override
    public void error(CharSequence content, Throwable error) {
        errors.add(content.toString());
    }

    @Override
    public void error(Throwable error) {
    }
}
