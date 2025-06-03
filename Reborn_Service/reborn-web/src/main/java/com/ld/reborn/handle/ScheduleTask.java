package com.ld.reborn.handle;

import com.ld.reborn.dao.HistoryInfoMapper;
import com.ld.reborn.constants.CommonConst;
import com.ld.reborn.utils.cache.RebornCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
@EnableScheduling
@Slf4j
public class ScheduleTask {

    @Autowired
    private HistoryInfoMapper historyInfoMapper;

    @Scheduled(cron = "0 0 0 * * ?")
    public void cleanIpHistory() {
        CopyOnWriteArraySet<String> ipHistory = (CopyOnWriteArraySet<String>) RebornCache.get(CommonConst.IP_HISTORY);
        if (ipHistory == null) {
            ipHistory = new CopyOnWriteArraySet<>();
            RebornCache.put(CommonConst.IP_HISTORY, ipHistory);
        }
        ipHistory.clear();

        RebornCache.remove(CommonConst.IP_HISTORY_STATISTICS);
        Map<String, Object> history = new HashMap<>();
        history.put(CommonConst.IP_HISTORY_PROVINCE, historyInfoMapper.getHistoryByProvince());
        history.put(CommonConst.IP_HISTORY_IP, historyInfoMapper.getHistoryByIp());
        history.put(CommonConst.IP_HISTORY_HOUR, historyInfoMapper.getHistoryBy24Hour());
        history.put(CommonConst.IP_HISTORY_COUNT, historyInfoMapper.getHistoryCount());
        RebornCache.put(CommonConst.IP_HISTORY_STATISTICS, history);
    }
}
