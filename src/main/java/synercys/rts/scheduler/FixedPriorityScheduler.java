package synercys.rts.scheduler;

import synercys.rts.framework.event.EventContainer;
import synercys.rts.framework.event.SchedulerIntervalEvent;
import synercys.rts.framework.Job;
import synercys.rts.framework.Task;
import synercys.rts.framework.TaskSet;
import java.util.HashMap;

/**
 * FixedPriorityScheduler.java
 * Purpose: A scheduler for the preemptive, fixed-priority real-time scheduler using the rate-monotonic (RM) priority assignment.
 *
 * @author CY Chen (cchen140@illinois.edu)
 * @version 1.0 - 2018, 12/14
 */
public class FixedPriorityScheduler extends SchedulerSimulator implements Advanceable {

    // This map stores each task's next job instance, no matter it's arrived or not.
    HashMap<Task, Job> nextJobOfATask = new HashMap<>();

    public FixedPriorityScheduler(TaskSet taskSet, boolean runTimeVariation) {
        super(taskSet, runTimeVariation);
        this.taskSet.assignPriorityRm();

        /* Initialize the first job of each task. */
        initializeFirstTaskJobs();
    }

    /**
     * Run simulation and advance to next scheduling point.
     */
    @Override
    public void advance() {
        Job currentJob = getNextJob(tick);

        // If it is a future job, then jump the tick first.
        if (currentJob.releaseTime > tick)
            tick = currentJob.releaseTime;

        // Run the job (and log the execution interval).
        tick = runJobToNextSchedulingPoint(tick, currentJob);
    }

    private Job getNextJob(long tick) {
        Job targetJob = null;
        int highestActivePriority = 0;

        for (Job job : nextJobOfATask.values()) {
            if (job.releaseTime > tick)
                continue;
            if (job.task.getPriority() > highestActivePriority) {
                highestActivePriority = job.task.getPriority();
                targetJob = job;
            }
        }
        if (targetJob != null)
            return targetJob;

        /* No job is active at this given tick point, so let's check who is the first job in the future. */
        long earliestNextReleaseTime = Long.MAX_VALUE;
        for (Job job : nextJobOfATask.values()) {
            if (job.releaseTime < earliestNextReleaseTime) {
                earliestNextReleaseTime = job.releaseTime;
                targetJob = job;
            }
        }

        return targetJob;
    }

    private long runJobToNextSchedulingPoint(long tick, Job runJob) {
        /* Find if there is any job preempting the runJob. */
        long earliestPreemptingJobReleaseTime = Long.MAX_VALUE;
        Job earliestPreemptingJob = null;
        long runJobFinishTime = tick + runJob.remainingExecTime;
        for (Job job: nextJobOfATask.values()) {
            if (job == runJob)
                continue;

            if (job.releaseTime < runJobFinishTime) {
                if (job.task.getPriority() > runJob.task.getPriority()) {
                    if (job.releaseTime < earliestPreemptingJobReleaseTime) {
                        earliestPreemptingJobReleaseTime = job.releaseTime;
                        earliestPreemptingJob = job;
                    }
                }
            }
        }

        // Check if any new job will preempt runJob.
        if (earliestPreemptingJob == null) {
            /* This job is finished. */
            runJob.remainingExecTime = 0;

            /* Log the job interval. */
            SchedulerIntervalEvent currentJobEvent = new SchedulerIntervalEvent(tick, runJobFinishTime, runJob.task, "");
            if ( runJob.hasStarted == false ) { // Check this job's starting state.
                runJob.hasStarted = true;
                currentJobEvent.setScheduleStates(SchedulerIntervalEvent.SCHEDULE_STATE_START, SchedulerIntervalEvent.SCHEDULE_STATE_END);
            } else {
                currentJobEvent.setScheduleStates(SchedulerIntervalEvent.SCHEDULE_STATE_RESUME, SchedulerIntervalEvent.SCHEDULE_STATE_END);
            }
            simEventContainer.add(currentJobEvent);

            advanceToNextJob(runJob.task);

            // No one will preempt runJob, so runJob is good to finish its job.
            return runJobFinishTime;
        } else {
            /* This job is preempted. */
            // runJob will be preempted before it's finished, so update runJob's remaining execution time.
            runJob.remainingExecTime -= (earliestPreemptingJobReleaseTime - tick);

            /* Log the job interval. */
            SchedulerIntervalEvent currentJobEvent = new SchedulerIntervalEvent(tick, earliestPreemptingJobReleaseTime, runJob.task, "");
            if ( runJob.hasStarted == false ) { // Check this job's starting state.
                runJob.hasStarted = true;
                currentJobEvent.setScheduleStates(SchedulerIntervalEvent.SCHEDULE_STATE_START, SchedulerIntervalEvent.SCHEDULE_STATE_SUSPEND);
            } else {
                currentJobEvent.setScheduleStates(SchedulerIntervalEvent.SCHEDULE_STATE_RESUME, SchedulerIntervalEvent.SCHEDULE_STATE_SUSPEND);
            }
            simEventContainer.add(currentJobEvent);

            return earliestPreemptingJobReleaseTime;
        }
    }

    @Override
    public EventContainer runSim(long tickLimit) {
        tick = 0;

        while (tick <= tickLimit) {
            advance();
        }
        simEventContainer.trimEventsToTimeStamp(tickLimit);

        simEventContainer.setSchedulingPolicy(EventContainer.SCHEDULING_POLICY_FIXED_PRIORITY);
        return simEventContainer;
    }

    @Override
    protected void setTaskSetHook() {

    }

    private Job advanceToNextJob(Task task) {
        /* Determine next arrival time. */
        long nextArrivalTime;
        if (task.isSporadicTask()) {
            nextArrivalTime = nextJobOfATask.get(task).releaseTime + getVariedInterArrivalTime(task);
        } else {
            nextArrivalTime = nextJobOfATask.get(task).releaseTime + task.getPeriod();
        }

        /* Determine the execution time. */
        long executionTime;
        if (runTimeVariation == true) {
            executionTime = getVariedExecutionTime(task);
        } else {
            executionTime = task.getWcet();
        }

        Job newJob = new Job(task, nextArrivalTime, executionTime);
        nextJobOfATask.put(task, newJob);

        return newJob;
    }

    private void initializeFirstTaskJobs() {
        for (Task task: taskSet.getRunnableTasksAsArray()) {
            Job firstJob;
            if (runTimeVariation == true)
                firstJob = new Job(task, task.getInitialOffset(), getVariedExecutionTime(task));
            else
                firstJob = new Job(task, task.getInitialOffset(), task.getWcet());
            nextJobOfATask.put(task, firstJob);
        }
    }
}
