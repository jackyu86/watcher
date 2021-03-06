package com.whh.watcher.receiver;


import com.whh.watcher.domain.Event;
import com.whh.watcher.spi.MessageQueue;
import com.whh.watcher.spi.impl.DefaultMessageQueue;
import com.whh.watcher.statistic.ServerStatisticManager;
import com.whh.watcher.utils.Threads;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Map.Entry;

public class Period {
    private long m_startTime;
    private long m_endTime;
    private Map<String, List<PeriodTask>> m_tasks;
    private MessageAnalyzerManager m_analyzerManager;
    private ServerStatisticManager m_serverStateManager;
    private Logger logger = LoggerFactory.getLogger(Period.class);
    private static int QUEUE_SIZE = 30000;

    public Period(long startTime, long endTime, MessageAnalyzerManager analyzerManager,
                  ServerStatisticManager serverStateManager) {
        m_startTime = startTime;
        m_endTime = endTime;
        m_analyzerManager = analyzerManager;
//        m_serverStateManager = serverStateManager;
        List<String> names = m_analyzerManager.getAnalyzerNames();

        m_tasks = new HashMap<String, List<PeriodTask>>();
        for (String name : names) {
            List<MessageAnalyzer> messageAnalyzers = m_analyzerManager.getAnalyzer(name, startTime);
            for (MessageAnalyzer analyzer : messageAnalyzers) {
                MessageQueue queue = new DefaultMessageQueue(QUEUE_SIZE);
                PeriodTask task = new PeriodTask(analyzer, queue, startTime);

                List<PeriodTask> analyzerTasks = m_tasks.get(name);

                if (analyzerTasks == null) {
                    analyzerTasks = new ArrayList<PeriodTask>();
                    m_tasks.put(name, analyzerTasks);
                }
                analyzerTasks.add(task);
            }
        }
    }

    /**
     * 事件分发
     *
     * @param event 接收到的事件
     */
    public void distribute(Event event) {
//        m_serverStateManager.addMessageTotal(event.getApp(), 1);
        boolean success = true;
        String domain = event.getApp();

        for (Entry<String, List<PeriodTask>> entry : m_tasks.entrySet()) {
            List<PeriodTask> tasks = entry.getValue();
            int length = tasks.size();
            int index = 0;
            boolean manyTasks = length > 1;

            if (manyTasks) {
                index = Math.abs(domain.hashCode()) % length;
            }
            PeriodTask task = tasks.get(index);
            boolean enqueue = task.enqueue(event);

            if (enqueue == false) {
                if (manyTasks) {
                    task = tasks.get((index + 1) % length);
                    enqueue = task.enqueue(event);

                    if (enqueue == false) {
                        success = false;
                    }
                } else {
                    success = false;
                }
            }
        }
        if (!success) {
            m_serverStateManager.addMessageTotalLoss(event.getApp(), 1);
        }
    }

    public void finish() {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date startDate = new Date(m_startTime);
        Date endDate = new Date(m_endTime - 1);
        logger.info("Finishing {} tasks in period [{}, {}]", m_tasks.size(), df.format(startDate), df.format(endDate));
        try {
            for (Entry<String, List<PeriodTask>> tasks : m_tasks.entrySet()) {
                for (PeriodTask task : tasks.getValue()) {
                    task.finish();
                }
            }
        } catch (Throwable e) {
            logger.error("结束周期任务异常：", e);
        } finally {
            logger.info("Finished {} tasks in period [{}, {}]", m_tasks.size(), df.format(startDate),
                    df.format(endDate));
        }
    }

    public List<MessageAnalyzer> getAnalyzer(String name) {
        List<MessageAnalyzer> analyzers = new ArrayList<MessageAnalyzer>();
        List<PeriodTask> tasks = m_tasks.get(name);

        if (tasks != null) {
            for (PeriodTask task : tasks) {
                analyzers.add(task.getAnalyzer());
            }
        }
        return analyzers;
    }

    public List<MessageAnalyzer> getAnalzyers() {
        List<MessageAnalyzer> analyzers = new ArrayList<MessageAnalyzer>(m_tasks.size());

        for (Entry<String, List<PeriodTask>> tasks : m_tasks.entrySet()) {
            for (PeriodTask task : tasks.getValue()) {
                analyzers.add(task.getAnalyzer());
            }
        }

        return analyzers;
    }

    public long getStartTime() {
        return m_startTime;
    }

    public boolean isIn(long timestamp) {
        return timestamp >= m_startTime && timestamp < m_endTime;
    }

    public void start() {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        logger.info("Starting {} tasks in period [{}, {}]", m_tasks.size(),
                df.format(new Date(m_startTime)), df.format(new Date(m_endTime - 1)));

        for (Entry<String, List<PeriodTask>> tasks : m_tasks.entrySet()) {
            List<PeriodTask> taskList = tasks.getValue();
            for (int i = 0; i < taskList.size(); i++) {
                PeriodTask task = taskList.get(i);
                task.setIndex(i);
                Threads.forGroup("Cat-RealtimeConsumer").start(task);
            }
        }
    }

}
