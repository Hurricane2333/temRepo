package com.ld.reborn.config;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.ld.reborn.dao.HistoryInfoMapper;
import com.ld.reborn.dao.WebInfoMapper;
import com.ld.reborn.entity.*;
import com.ld.reborn.im.websocket.TioUtil;
import com.ld.reborn.im.websocket.TioWebsocketStarter;
import com.ld.reborn.service.UserService;
import com.ld.reborn.constants.CommonConst;
import com.ld.reborn.utils.cache.RebornCache;
import com.ld.reborn.enums.RebornEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;

@Component
public class RebornApplicationRunner implements ApplicationRunner {

    @Value("${store.type}")
    private String defaultType;

    @Autowired
    private WebInfoMapper webInfoMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private HistoryInfoMapper historyInfoMapper;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        LambdaQueryChainWrapper<WebInfo> wrapper = new LambdaQueryChainWrapper<>(webInfoMapper);
        List<WebInfo> list = wrapper.list();
        if (!CollectionUtils.isEmpty(list)) {
            list.get(0).setDefaultStoreType(defaultType);
            RebornCache.put(CommonConst.WEB_INFO, list.get(0));
        }

        User admin = userService.lambdaQuery().eq(User::getUserType, RebornEnum.USER_TYPE_ADMIN.getCode()).one();
        RebornCache.put(CommonConst.ADMIN, admin);

        List<HistoryInfo> infoList = new LambdaQueryChainWrapper<>(historyInfoMapper)
                .select(HistoryInfo::getIp, HistoryInfo::getUserId)
                .ge(HistoryInfo::getCreateTime, LocalDateTime.now().with(LocalTime.MIN))
                .list();

        RebornCache.put(CommonConst.IP_HISTORY, new CopyOnWriteArraySet<>(infoList.stream().map(info -> info.getIp() + (info.getUserId() != null ? "_" + info.getUserId().toString() : "")).collect(Collectors.toList())));

        Map<String, Object> history = new HashMap<>();
        history.put(CommonConst.IP_HISTORY_PROVINCE, historyInfoMapper.getHistoryByProvince());
        history.put(CommonConst.IP_HISTORY_IP, historyInfoMapper.getHistoryByIp());
        history.put(CommonConst.IP_HISTORY_HOUR, historyInfoMapper.getHistoryBy24Hour());
        history.put(CommonConst.IP_HISTORY_COUNT, historyInfoMapper.getHistoryCount());
        RebornCache.put(CommonConst.IP_HISTORY_STATISTICS, history);

        TioUtil.buildTio();
        TioWebsocketStarter websocketStarter = TioUtil.getTio();
        if (websocketStarter != null) {
            websocketStarter.start();
        }
    }
}
