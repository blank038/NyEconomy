package com.mc9y.nyeconomy.cron;

import com.mc9y.nyeconomy.Main;
import com.mc9y.nyeconomy.handler.AbstractStorgeHandler;
import org.quartz.Job;
import org.quartz.JobExecutionContext;

import java.util.List;
import java.util.logging.Level;

public class CronResetJob implements Job {
    @Override
    public void execute(JobExecutionContext context) {
        String jobKey = context.getMergedJobDataMap().getString("JobKey");
        List<String> currencies = CronResetManager.getJobCurrencies(jobKey);
        if (currencies == null || currencies.isEmpty()) {
            return;
        }
        for (String currency : currencies) {
            try {
                int affected = AbstractStorgeHandler.getHandler().resetAll(currency);
                Main.getInstance().getLogger().info("[CronReset] " + jobKey + " - " + currency + " reset " + affected + " records");
            } catch (Exception e) {
                Main.getInstance().getLogger().log(Level.WARNING, "[CronReset] " + jobKey + " - " + currency + " reset failed", e);
            }
        }
    }
}
