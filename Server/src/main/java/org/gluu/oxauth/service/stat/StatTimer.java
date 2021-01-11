package org.gluu.oxauth.service.stat;

import org.gluu.oxauth.model.configuration.AppConfiguration;
import org.gluu.oxauth.service.cdi.event.StatEvent;
import org.gluu.service.cdi.async.Asynchronous;
import org.gluu.service.cdi.event.Scheduled;
import org.gluu.service.timer.event.TimerEvent;
import org.gluu.service.timer.schedule.TimerSchedule;
import org.slf4j.Logger;

import javax.ejb.DependsOn;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Yuriy Zabrovarnyy
 */
@ApplicationScoped
@DependsOn("appInitializer")
@Named
public class StatTimer {

    private static final int TIMER_TICK_INTERVAL_IN_SECONDS = 4; // 1 min
    private static final int TIMER_INTERVAL_IN_SECONDS = 4;//15 * 60; // 15 min

    @Inject
    private Logger log;

    @Inject
    private Event<TimerEvent> timerEvent;

    @Inject
    private AppConfiguration appConfiguration;

    @Inject
    private StatService statService;

    private AtomicBoolean isActive;
    private long lastFinishedTime;

    public void initTimer() {
        log.debug("Initializing Stat Service Timer");

        final boolean initialized = statService.init();
        if (!initialized) {
            log.error("Failed to initialize Stat Service Timer.");
            return;
        }
        this.isActive = new AtomicBoolean(false);

        timerEvent.fire(new TimerEvent(new TimerSchedule(TIMER_TICK_INTERVAL_IN_SECONDS, TIMER_TICK_INTERVAL_IN_SECONDS), new StatEvent(), Scheduled.Literal.INSTANCE));

        this.lastFinishedTime = System.currentTimeMillis();
        log.debug("Initialized Stat Service Timer");
    }

    @Asynchronous
    public void process(@Observes @Scheduled StatEvent event) {
        if (!appConfiguration.getStatEnabled()) {
            return;
        }

        if (this.isActive.get()) {
            return;
        }

        if (!this.isActive.compareAndSet(false, true)) {
            return;
        }

        try {
            if (!allowToRun()) {
                return;
            }
            statService.updateStat();
            this.lastFinishedTime = System.currentTimeMillis();
        } catch (Exception ex) {
            log.error("Exception happened while updating stat", ex);
        } finally {
            this.isActive.set(false);
        }
    }

    private boolean allowToRun() {
        int interval = appConfiguration.getStatTimerIntervalInSeconds();
        if (interval < 0) {
            log.info("Stat Timer is disabled.");
            log.warn("Stat Timer Interval (statTimerIntervalInSeconds in server configuration) is negative which turns OFF statistic on the server. Please set it to positive value if you wish it to run.");
            return false;
        }
        if (interval == 0)
            interval = TIMER_INTERVAL_IN_SECONDS;

        long timerInterval = interval * 1000;

        long timeDiff = System.currentTimeMillis() - this.lastFinishedTime;

        return timeDiff >= timerInterval;
    }
}
