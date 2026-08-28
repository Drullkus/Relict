package us.drullk.relict.companion;

/**
 * One unit of in-process work, polled once per client tick until it reports done. Verbs plan a job as a
 * short sequence of these instead of blocking the tick thread directly -- most steps finish on their
 * first poll (a command dispatch, a math computation), a few need a handful of ticks (a network round
 * trip settling, a screenshot's IO-thread write finishing), and none of them ever sleep.
 */
@FunctionalInterface
interface Step {

    /**
     * @return true once this step's work is complete and the job may advance to the next step.
     */
    boolean tick(JobContext ctx) throws Exception;

}
