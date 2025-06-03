package com.ld.reborn.controller;


import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.ld.reborn.aop.LoginCheck;
import com.ld.reborn.config.RebornResult;
import com.ld.reborn.constants.CommonConst;
import com.ld.reborn.dao.*;
import com.ld.reborn.entity.*;
import com.ld.reborn.service.WebInfoService;
import com.ld.reborn.utils.*;
import com.ld.reborn.utils.cache.RebornCache;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 网站信息表 前端控制器
 * </p>
 */
@RestController
@RequestMapping("/webInfo")
public class WebInfoController {

    @Value("${store.type}")
    private String defaultType;

    @Autowired
    private WebInfoService webInfoService;

    @Autowired
    private HistoryInfoMapper historyInfoMapper;

    @Autowired
    private CommonQuery commonQuery;


    /**
     * 更新网站信息
     */
    @LoginCheck(0)
    @PostMapping("/updateWebInfo")
    public RebornResult<WebInfo> updateWebInfo(@RequestBody WebInfo webInfo) {
        webInfoService.updateById(webInfo);

        LambdaQueryChainWrapper<WebInfo> wrapper = new LambdaQueryChainWrapper<>(webInfoService.getBaseMapper());
        List<WebInfo> list = wrapper.list();
        if (!CollectionUtils.isEmpty(list)) {
            list.get(0).setDefaultStoreType(defaultType);
            RebornCache.put(CommonConst.WEB_INFO, list.get(0));
        }
        return RebornResult.success();
    }


    /**
     * 获取网站信息
     */
    @GetMapping("/getWebInfo")
    public RebornResult<WebInfo> getWebInfo() {
        WebInfo webInfo = (WebInfo) RebornCache.get(CommonConst.WEB_INFO);
        if (webInfo != null) {
            WebInfo result = new WebInfo();
            BeanUtils.copyProperties(webInfo, result);
            result.setRandomAvatar(null);
            result.setRandomName(null);
            result.setWaifuJson(null);

            webInfo.setHistoryAllCount(((Long) ((Map<String, Object>) RebornCache.get(CommonConst.IP_HISTORY_STATISTICS)).get(CommonConst.IP_HISTORY_COUNT)).toString());
            webInfo.setHistoryDayCount(Integer.toString(((List<Map<String, Object>>) ((Map<String, Object>) RebornCache.get(CommonConst.IP_HISTORY_STATISTICS)).get(CommonConst.IP_HISTORY_HOUR)).size()));
            return RebornResult.success(result);
        }
        return RebornResult.success();
    }

    /**
     * 获取网站统计信息
     */
    @LoginCheck(0)
    @GetMapping("/getHistoryInfo")
    public RebornResult<Map<String, Object>> getHistoryInfo() {
        Map<String, Object> result = new HashMap<>();

        Map<String, Object> history = (Map<String, Object>) RebornCache.get(CommonConst.IP_HISTORY_STATISTICS);
        List<HistoryInfo> infoList = new LambdaQueryChainWrapper<>(historyInfoMapper)
                .select(HistoryInfo::getIp, HistoryInfo::getUserId, HistoryInfo::getNation, HistoryInfo::getProvince, HistoryInfo::getCity)
                .ge(HistoryInfo::getCreateTime, LocalDateTime.now().with(LocalTime.MIN))
                .list();

        result.put(CommonConst.IP_HISTORY_PROVINCE, history.get(CommonConst.IP_HISTORY_PROVINCE));
        result.put(CommonConst.IP_HISTORY_IP, history.get(CommonConst.IP_HISTORY_IP));
        result.put(CommonConst.IP_HISTORY_COUNT, history.get(CommonConst.IP_HISTORY_COUNT));
        List<Map<String, Object>> ipHistoryCount = (List<Map<String, Object>>) history.get(CommonConst.IP_HISTORY_HOUR);
        result.put("ip_count_yest", ipHistoryCount.stream().map(m -> m.get("ip")).distinct().count());
        result.put("username_yest", ipHistoryCount.stream().map(m -> {
            Object userId = m.get("user_id");
            if (userId != null) {
                User user = commonQuery.getUser(Integer.valueOf(userId.toString()));
                if (user != null) {
                    Map<String, String> userInfo = new HashMap<>();
                    userInfo.put("avatar", user.getAvatar());
                    userInfo.put("username", user.getUsername());
                    return userInfo;
                }
            }
            return null;
        }).filter(Objects::nonNull).collect(Collectors.toList()));
        result.put("ip_count_today", infoList.stream().map(HistoryInfo::getIp).distinct().count());
        result.put("username_today", infoList.stream().map(m -> {
            Integer userId = m.getUserId();
            if (userId != null) {
                User user = commonQuery.getUser(userId);
                if (user != null) {
                    Map<String, String> userInfo = new HashMap<>();
                    userInfo.put("avatar", user.getAvatar());
                    userInfo.put("username", user.getUsername());
                    return userInfo;
                }
            }
            return null;
        }).filter(Objects::nonNull).collect(Collectors.toList()));

        List<Map<String, Object>> list = infoList.stream()
                .map(HistoryInfo::getProvince).filter(Objects::nonNull)
                .collect(Collectors.groupingBy(m -> m, Collectors.counting()))
                .entrySet().stream()
                .map(entry -> {
                    HashMap<String, Object> map = new HashMap<>();
                    map.put("province", entry.getKey());
                    map.put("num", entry.getValue());
                    return map;
                })
                .sorted((o1, o2) -> Long.valueOf(o2.get("num").toString()).compareTo(Long.valueOf(o1.get("num").toString())))
                .collect(Collectors.toList());

        result.put("province_today", list);

        return RebornResult.success(result);
    }

    /**
     * 获取赞赏
     */
    @GetMapping("/getAdmire")
    public RebornResult<List<User>> getAdmire() {
        return RebornResult.success(commonQuery.getAdmire());
    }

    /**
     * 获取看板娘消息
     */
    @GetMapping("/getWaifuJson")
    public String getWaifuJson() {
        WebInfo webInfo = (WebInfo) RebornCache.get(CommonConst.WEB_INFO);
        if (webInfo != null && StringUtils.hasText(webInfo.getWaifuJson())) {
            return webInfo.getWaifuJson();
        }
        return "{}";
    }
}

