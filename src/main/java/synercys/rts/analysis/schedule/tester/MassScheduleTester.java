package synercys.rts.analysis.schedule.tester;

import cy.utility.file.FileHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import synercys.rts.analysis.MassTester;
import synercys.rts.analysis.schedule.ScheduleAnalysisReport;
import synercys.rts.framework.TaskSet;
import synercys.rts.scheduler.TaskSetContainer;
import synercys.rts.scheduler.TaskSetGenerator;

public class MassScheduleTester extends MassTester {
    // This test takes -d parameter in the unit of "the number of LCM"
    // This test yields "mean response time ratio to period" and "mean execution range ratio to period" for each task set
    public static final String TEST_CASES_SCHEDULEAK_DURATION = "SLEAK_DURATION";

    private static final Logger loggerConsole = LogManager.getLogger("console");

    public MassScheduleTester(String logFilePath, TaskSetContainer taskSetContainer) {
        super(logFilePath, taskSetContainer);
    }

    @Override
    public boolean run(String testCase) {
        return runScheduLeakDurationTest();
        // return false;
    }

    protected boolean runScheduLeakDurationTest() {
        loggerConsole.info("Scheduler: {}", schedulingPolicy);

        FileHandler fileTestLog = openLogFileToWrite("sleaklcm", "csv");

        // title row
        fileTestLog.writeString(
                "Task Set ID,"
                        + "Raw Task Set ID,"
                        + "The Number of Tasks,"
                        + "Utilization,"
                        + "Hyper Period,"
                        + "Test Length,"
                        + "Context Switches,"
                        + "Mean Response Time Ratio To Period,"
                        + "Mean Execution Range Ratio To Period"
        );
        fileTestLog.writeString("\n");

        int taskSetCount = 0;
        int totalNumberOfTaskSet = taskSetContainer.size();
        long lcmCount = runDuration;
        for (TaskSet taskSet : taskSetContainer.getTaskSets()) {
            taskSetCount++;
            loggerConsole.info("Testing TaskSet #{}\t{}/{} ...", taskSet.getId(), taskSetCount, totalNumberOfTaskSet);

            ScheduleTester tester = new ScheduleTester(taskSet, schedulingPolicy, executionVariation);
            runDuration = TaskSetGenerator.getLCMDurationOfDefaultObserverVictimTasks(taskSet)*lcmCount; // from lcm to ticks
            ScheduleAnalysisReport report = (ScheduleAnalysisReport)tester.run(runDuration);
            double meanResponseTimeRatioToPeriod = report.getMeanResponseTimeRatioToPeriod();
            double meanExecutionRangeRatioToPeriod = report.getMeanTaskExecutionRangeRatioToPeriod();

            fileTestLog.writeString(taskSetCount + ",");
            fileTestLog.writeString(taskSet.getId() + ",");
            fileTestLog.writeString(taskSet.getRunnableTasksAsArray().size() + ",");
            fileTestLog.writeString(taskSet.getUtilization() + ",");
            fileTestLog.writeString(taskSet.calHyperPeriod() + ",");
            fileTestLog.writeString(runDuration + ",");
            fileTestLog.writeString(report.contextSwitches + ",");
            fileTestLog.writeString(String.format("%.4f", meanResponseTimeRatioToPeriod) + ",");
            fileTestLog.writeString(String.format("%.4f", meanExecutionRangeRatioToPeriod) + "\n");

            loggerConsole.info("\tDone: MeanResponseTimeRatio={},\tMeanExecutionRangeRatio={},\tContextSwitchCount={}",
                    String.format("%.4f", meanResponseTimeRatioToPeriod),
                    String.format("%.4f", meanExecutionRangeRatioToPeriod),
                    report.contextSwitches);

        }

        return true;
    }
}
