package com.jd.watcher.receiver.impl;

import com.jd.watcher.CatConstants;
import com.jd.watcher.Watcher;
import com.jd.watcher.common.Initialization;
import com.jd.watcher.domain.Event;
import com.jd.watcher.domain.WatchEvent;
import com.jd.watcher.exception.NoRetryException;
import com.jd.watcher.receiver.*;
import com.jd.watcher.spi.BlackListManager;
import com.jd.watcher.statistic.ServerStatisticManager;
import com.jd.watcher.utils.Threads;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

@Service("realTimeConsumer")
public class RealTimeConsumer implements MessageConsumer, Initialization {
    @Resource
    private MessageAnalyzerManager analyzerManager;
    @Resource
    private ServerStatisticManager statisticManager;
    @Resource
    private BlackListManager blackListManager;
    private PeriodManager periodManager;
    private static Logger logger = LoggerFactory.getLogger(RealTimeConsumer.class);

    private long m_black = -1;
    public static final long MINUTE = 60 * 1000L;
    public static final long HOUR = 60 * MINUTE;

    @Override
    public void consume(Event event) {
        String domain = event.getApp();
        String ip = event.getHostIp();


        if (!blackListManager.isBlackList(domain, ip)) {
            long timestamp = event.getTime();
            Period period = periodManager.findPeriod(timestamp);
            if (period != null) {
                period.distribute(event);
            } else {
                statisticManager.addNetworkTimeError(1);
            }
        } else {
            m_black++;
            if (m_black % CatConstants.SUCCESS_COUNT == 0) {
                Watcher.watchForCount("discard_" + domain);
            }
        }
    }

    public void doCheckpoint() {
        logger.info("starting do checkpoint.");
        WatchEvent watchEvent = Watcher.watchStart("checkpoint_test", "service");
        try {
            long currentStartTime = getCurrentStartTime();
            Period period = periodManager.findPeriod(currentStartTime);

            for (MessageAnalyzer analyzer : period.getAnalzyers()) {
                try {
                    analyzer.doCheckpoint(false);
                } catch (Exception e) {
                    Watcher.watchError(watchEvent, e);
                }
            }
            try {
                // wait dump analyzer store completed
                Thread.sleep(10 * 1000);
            } catch (InterruptedException e) {
                // ignore
            }
        } catch (RuntimeException e) {
            Watcher.watchError(watchEvent, e);
        } finally {
            Watcher.watchEnd(watchEvent);
        }
        logger.info("end do checkpoint.");
    }

    /**
     * 获取整小时的豪秒数
     *
     * @return
     */
    private long getCurrentStartTime() {
        long now = System.currentTimeMillis();
        long time = now - now % HOUR;

        return time;
    }

    @Override
    @PostConstruct
    public void initialize() throws NoRetryException {
        periodManager = new PeriodManager(HOUR, analyzerManager, statisticManager);
        periodManager.init();
        Threads.forGroup("cat").start(periodManager);
    }

    public static void main(String[] args) {
        System.out.println(new RealTimeConsumer().getCurrentStartTime());
    }
}