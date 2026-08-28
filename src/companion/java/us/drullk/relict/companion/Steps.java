package us.drullk.relict.companion;

/**
 * Small {@link Step} combinators shared by every verb -- nothing here is verb-specific.
 */
final class Steps {

    private Steps() {
    }

    interface ThrowingRunnable {
        void run(JobContext ctx) throws Exception;
    }

    /** Runs {@code action} once, on the tick it is first polled, then reports done immediately. */
    static Step once(ThrowingRunnable action) {
        boolean[] done = {false};
        return ctx -> {
            if (!done[0]) {
                action.run(ctx);
                done[0] = true;
            }
            return true;
        };
    }

    /** Waits out a fixed number of ticks -- e.g. letting a teleport's server-to-client sync land before a
     * later step reads the client's own position/world state back. */
    static Step settle(int ticks) {
        int[] remaining = {Math.max(ticks, 0)};
        return ctx -> remaining[0]-- <= 0;
    }

    /** Defers building the real (possibly multi-tick, possibly stateful) step until the first poll, then
     * delegates to that same instance on every later poll -- needed whenever a step's command depends on
     * a value only known once an earlier step has already run (planning happens up front, before
     * execution). Building the delegate exactly once matters: a stateful step like
     * {@link CommandExec#run} would resubmit its command on every poll if rebuilt fresh each time. */
    static Step lazy(java.util.function.Function<JobContext, Step> factory) {
        Step[] delegate = new Step[1];
        return ctx -> {
            if (delegate[0] == null) {
                delegate[0] = factory.apply(ctx);
            }
            return delegate[0].tick(ctx);
        };
    }

    interface ThrowingPredicate {
        boolean test(JobContext ctx) throws Exception;
    }

    /** Polls {@code condition} every tick until it is true, up to {@code timeoutTicks}; past that, fails
     * the job with {@code timeoutMessage} rather than hanging rigctl's caller silently. */
    static Step waitUntil(ThrowingPredicate condition, int timeoutTicks, String timeoutMessage) {
        int[] elapsed = {0};
        return ctx -> {
            if (condition.test(ctx)) {
                return true;
            }
            if (elapsed[0]++ >= timeoutTicks) {
                throw new CompanionException(timeoutMessage);
            }
            return false;
        };
    }

}
