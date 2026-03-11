package com.mc9y.nyeconomy.cron;

import com.mc9y.nyeconomy.Main;
import org.bukkit.configuration.ConfigurationSection;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;

import java.util.*;
import java.util.logging.Level;

public class CronResetManager {
    private static Scheduler scheduler;
    private static final Map<String, List<String>> JOB_CURRENCIES = new HashMap<>();

    public static void initialize() {
        try {
            if (scheduler == null) {
                StdSchedulerFactory factory = new StdSchedulerFactory();
                factory.initialize(Main.getInstance().getResource("com/mc9y/nyeconomy/quartz.properties"));
                scheduler = factory.getScheduler();
            }
            loadJobs();
        } catch (SchedulerException e) {
            Main.getInstance().getLogger().log(Level.SEVERE, "Cron scheduler initialization failed", e);
        }
    }

    public static void shutdown() {
        if (scheduler != null) {
            try {
                scheduler.shutdown(true);
                scheduler = null;
            } catch (SchedulerException e) {
                Main.getInstance().getLogger().log(Level.WARNING, "Cron scheduler shutdown failed", e);
            }
        }
    }

    public static void reload() {
        if (scheduler == null) {
            return;
        }
        try {
            scheduler.clear();
            JOB_CURRENCIES.clear();
            loadJobs();
        } catch (SchedulerException e) {
            Main.getInstance().getLogger().log(Level.WARNING, "Cron scheduler reload failed", e);
        }
    }

    private static void loadJobs() throws SchedulerException {
        ConfigurationSection section = Main.getInstance().getConfig().getConfigurationSection("cron-reset");
        if (section == null) {
            Main.getInstance().getLogger().info("[CronReset] No cron-reset configuration found");
            return;
        }

        int count = 0;
        for (String key : section.getKeys(false)) {
            String expression = section.getString(key + ".expression");
            List<String> currencies = section.getStringList(key + ".currencies");

            if (expression == null || expression.isEmpty()) {
                Main.getInstance().getLogger().warning("[CronReset] " + key + " missing expression, skipped");
                continue;
            }
            if (currencies.isEmpty()) {
                Main.getInstance().getLogger().warning("[CronReset] " + key + " missing currencies, skipped");
                continue;
            }

            if (!CronExpression.isValidExpression(expression)) {
                Main.getInstance().getLogger().warning("[CronReset] " + key + " invalid cron expression: " + expression);
                continue;
            }

            JOB_CURRENCIES.put(key, currencies);

            JobDetail jobDetail = JobBuilder.newJob(CronResetJob.class)
                    .withIdentity(key, "nyeconomyResetGroup")
                    .usingJobData("JobKey", key)
                    .build();

            Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(key + "Trigger", "nyeconomyResetTriggerGroup")
                    .withSchedule(CronScheduleBuilder.cronSchedule(expression))
                    .build();

            scheduler.scheduleJob(jobDetail, trigger);
            count++;
        }

        if (!scheduler.isStarted()) {
            scheduler.start();
        }

        Main.getInstance().getLogger().info("[CronReset] Loaded " + count + " cron reset job(s)");
    }

    public static List<String> getJobCurrencies(String jobKey) {
        return JOB_CURRENCIES.get(jobKey);
    }
}
