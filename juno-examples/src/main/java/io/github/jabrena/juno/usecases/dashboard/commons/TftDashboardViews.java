package io.github.jabrena.juno.usecases.dashboard.commons;

/**
 * The ids the dashboard registers its views under, and that a view passes to the navigator to open another; below
 * {@link io.github.jabrena.juno.api.mvc.ViewRouter#MAX_VIEWS}.
 */
public final class TftDashboardViews {
    public static final int HOME = 0;
    public static final int STATUS = 1;
    public static final int STORAGE = 2;

    private TftDashboardViews() {
    }
}
