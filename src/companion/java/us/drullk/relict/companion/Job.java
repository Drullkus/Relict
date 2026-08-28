package us.drullk.relict.companion;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * A verb's plan: a sequential queue of {@link Step}s. {@link #tick} drains as many steps as complete
 * synchronously within one client tick before returning, so a job made entirely of instant steps (most
 * command dispatches, all camera math) finishes in a single tick call rather than paying one client tick
 * of latency per step.
 */
final class Job {

    private final Deque<Step> steps;

    Job(List<Step> steps) {
        this.steps = new ArrayDeque<>(steps);
    }

    /** @return true once every step has completed. */
    boolean tick(JobContext ctx) throws Exception {
        while (!steps.isEmpty()) {
            if (steps.peek().tick(ctx)) {
                steps.poll();
            } else {
                return false;
            }
        }
        return true;
    }

}
