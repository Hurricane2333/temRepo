package com.ld.reborn.service.impl;

import cn.hutool.crypto.SecureUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ld.reborn.config.RebornResult;
import com.ld.reborn.constants.CommonConst;
import com.ld.reborn.dao.UserMapper;
import com.ld.reborn.entity.User;
import com.ld.reborn.entity.WebInfo;
import com.ld.reborn.enums.RebornEnum;
import com.ld.reborn.handle.RebornRuntimeException;
import com.ld.reborn.im.http.dao.ImChatGroupUserMapper;
import com.ld.reborn.im.http.dao.ImChatUserFriendMapper;
import com.ld.reborn.im.http.entity.ImChatGroupUser;
import com.ld.reborn.im.http.entity.ImChatUserFriend;
import com.ld.reborn.im.websocket.ImConfigConst;
import com.ld.reborn.im.websocket.TioUtil;
import com.ld.reborn.im.websocket.TioWebsocketStarter;
import com.ld.reborn.service.UserService;
import com.ld.reborn.utils.*;
import com.ld.reborn.utils.cache.RebornCache;
import com.ld.reborn.utils.mail.MailUtil;
import com.ld.reborn.vo.BaseRequestVO;
import com.ld.reborn.vo.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;
import org.tio.core.Tio;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * <p>
 * 用户信息表 服务实现类
 * </p>
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Autowired
    private ImChatGroupUserMapper imChatGroupUserMapper;

    @Autowired
    private ImChatUserFriendMapper imChatUserFriendMapper;

    @Autowired
    private MailUtil mailUtil;

    @Value("${user.code.format}")
    private String codeFormat;

    @Override
    public RebornResult<UserVO> login(String account, String password, Boolean isAdmin) {
        password = new String(SecureUtil.aes(CommonConst.CRYPOTJS_KEY.getBytes(StandardCharsets.UTF_8)).decrypt(password));

        User one = lambdaQuery().and(wrapper -> wrapper
                        .eq(User::getUsername, account)
                        .or()
                        .eq(User::getEmail, account)
                        .or()
                        .eq(User::getPhoneNumber, account))
                .eq(User::getPassword, DigestUtils.md5DigestAsHex(password.getBytes()))
                .one();

        if (one == null) {
            return RebornResult.fail("账号/密码错误，请重新输入！");
        }

        if (!one.getUserStatus()) {
            return RebornResult.fail("账号被冻结！");
        }

        String adminToken = "";
        String userToken = "";

        if (isAdmin) {
            if (one.getUserType() != RebornEnum.USER_TYPE_ADMIN.getCode() && one.getUserType() != RebornEnum.USER_TYPE_DEV.getCode()) {
                return RebornResult.fail("请输入管理员账号！");
            }
            if (RebornCache.get(CommonConst.ADMIN_TOKEN + one.getId()) != null) {
                adminToken = (String) RebornCache.get(CommonConst.ADMIN_TOKEN + one.getId());
            }
        } else {
            if (RebornCache.get(CommonConst.USER_TOKEN + one.getId()) != null) {
                userToken = (String) RebornCache.get(CommonConst.USER_TOKEN + one.getId());
            }
        }


        if (isAdmin && !StringUtils.hasText(adminToken)) {
            String uuid = UUID.randomUUID().toString().replaceAll("-", "");
            adminToken = CommonConst.ADMIN_ACCESS_TOKEN + uuid;
            RebornCache.put(adminToken, one, CommonConst.TOKEN_EXPIRE);
            RebornCache.put(CommonConst.ADMIN_TOKEN + one.getId(), adminToken, CommonConst.TOKEN_EXPIRE);
        } else if (!isAdmin && !StringUtils.hasText(userToken)) {
            String uuid = UUID.randomUUID().toString().replaceAll("-", "");
            userToken = CommonConst.USER_ACCESS_TOKEN + uuid;
            RebornCache.put(userToken, one, CommonConst.TOKEN_EXPIRE);
            RebornCache.put(CommonConst.USER_TOKEN + one.getId(), userToken, CommonConst.TOKEN_EXPIRE);
        }


        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(one, userVO);
        userVO.setPassword(null);
        if (isAdmin && one.getUserType() == RebornEnum.USER_TYPE_ADMIN.getCode()) {
            userVO.setIsBoss(true);
        }

        if (isAdmin) {
            userVO.setAccessToken(adminToken);
        } else {
            userVO.setAccessToken(userToken);
        }
        return RebornResult.success(userVO);
    }

    @Override
    public RebornResult exit() {
        String token = RebornUtil.getToken();
        Integer userId = RebornUtil.getUserId();
        if (token.contains(CommonConst.USER_ACCESS_TOKEN)) {
            RebornCache.remove(CommonConst.USER_TOKEN + userId);
            TioWebsocketStarter tioWebsocketStarter = TioUtil.getTio();
            if (tioWebsocketStarter != null) {
                Tio.removeUser(tioWebsocketStarter.getServerTioConfig(), String.valueOf(userId), "remove user");
            }
        } else if (token.contains(CommonConst.ADMIN_ACCESS_TOKEN)) {
            RebornCache.remove(CommonConst.ADMIN_TOKEN + userId);
        }
        RebornCache.remove(token);
        return RebornResult.success();
    }

    @Override
    public RebornResult<UserVO> regist(UserVO user) {
        String regex = "\\d{11}";
        if (user.getUsername().matches(regex)) {
            return RebornResult.fail("用户名不能为11位数字！");
        }

        if (user.getUsername().contains("@")) {
            return RebornResult.fail("用户名不能包含@！");
        }

        if (StringUtils.hasText(user.getPhoneNumber()) && StringUtils.hasText(user.getEmail())) {
            return RebornResult.fail("手机号与邮箱只能选择其中一个！");
        }

        if (StringUtils.hasText(user.getPhoneNumber())) {
            Integer codeCache = (Integer) RebornCache.get(CommonConst.FORGET_PASSWORD + user.getPhoneNumber() + "_1");
            if (codeCache == null || codeCache != Integer.parseInt(user.getCode())) {
                return RebornResult.fail("验证码错误！");
            }
            RebornCache.remove(CommonConst.FORGET_PASSWORD + user.getPhoneNumber() + "_1");
        } else if (StringUtils.hasText(user.getEmail())) {
            Integer codeCache = (Integer) RebornCache.get(CommonConst.FORGET_PASSWORD + user.getEmail() + "_2");
            if (codeCache == null || codeCache != Integer.parseInt(user.getCode())) {
                return RebornResult.fail("验证码错误！");
            }
            RebornCache.remove(CommonConst.FORGET_PASSWORD + user.getEmail() + "_2");
        } else {
            return RebornResult.fail("请输入邮箱或手机号！");
        }


        user.setPassword(new String(SecureUtil.aes(CommonConst.CRYPOTJS_KEY.getBytes(StandardCharsets.UTF_8)).decrypt(user.getPassword())));

        Integer count = lambdaQuery().eq(User::getUsername, user.getUsername()).count();
        if (count != 0) {
            return RebornResult.fail("用户名重复！");
        }
        if (StringUtils.hasText(user.getPhoneNumber())) {
            Integer phoneNumberCount = lambdaQuery().eq(User::getPhoneNumber, user.getPhoneNumber()).count();
            if (phoneNumberCount != 0) {
                return RebornResult.fail("手机号重复！");
            }
        } else if (StringUtils.hasText(user.getEmail())) {
            Integer emailCount = lambdaQuery().eq(User::getEmail, user.getEmail()).count();
            if (emailCount != 0) {
                return RebornResult.fail("邮箱重复！");
            }
        }

        User u = new User();
        u.setUsername(user.getUsername());
        u.setPhoneNumber(user.getPhoneNumber());
        u.setEmail(user.getEmail());
        u.setPassword(DigestUtils.md5DigestAsHex(user.getPassword().getBytes()));
        u.setAvatar(RebornUtil.getRandomAvatar(null));
        save(u);

        User one = lambdaQuery().eq(User::getId, u.getId()).one();

        String userToken = CommonConst.USER_ACCESS_TOKEN + UUID.randomUUID().toString().replaceAll("-", "");
        RebornCache.put(userToken, one, CommonConst.TOKEN_EXPIRE);
        RebornCache.put(CommonConst.USER_TOKEN + one.getId(), userToken, CommonConst.TOKEN_EXPIRE);

        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(one, userVO);
        userVO.setPassword(null);
        userVO.setAccessToken(userToken);

        ImChatGroupUser imChatGroupUser = new ImChatGroupUser();
        imChatGroupUser.setGroupId(ImConfigConst.DEFAULT_GROUP_ID);
        imChatGroupUser.setUserId(one.getId());
        imChatGroupUser.setUserStatus(ImConfigConst.GROUP_USER_STATUS_PASS);
        imChatGroupUserMapper.insert(imChatGroupUser);

        ImChatUserFriend imChatUser = new ImChatUserFriend();
        imChatUser.setUserId(one.getId());
        imChatUser.setFriendId(RebornUtil.getAdminUser().getId());
        imChatUser.setRemark("站长");
        imChatUser.setFriendStatus(ImConfigConst.FRIEND_STATUS_PASS);
        imChatUserFriendMapper.insert(imChatUser);

        ImChatUserFriend imChatFriend = new ImChatUserFriend();
        imChatFriend.setUserId(RebornUtil.getAdminUser().getId());
        imChatFriend.setFriendId(one.getId());
        imChatFriend.setFriendStatus(ImConfigConst.FRIEND_STATUS_PASS);
        imChatUserFriendMapper.insert(imChatFriend);

        return RebornResult.success(userVO);
    }

    @Override
    public RebornResult<UserVO> updateUserInfo(UserVO user) {
        if (StringUtils.hasText(user.getUsername())) {
            String regex = "\\d{11}";
            if (user.getUsername().matches(regex)) {
                return RebornResult.fail("用户名不能为11位数字！");
            }

            if (user.getUsername().contains("@")) {
                return RebornResult.fail("用户名不能包含@！");
            }

            Integer count = lambdaQuery().eq(User::getUsername, user.getUsername()).ne(User::getId, RebornUtil.getUserId()).count();
            if (count != 0) {
                return RebornResult.fail("用户名重复！");
            }
        }
        User u = new User();
        u.setId(RebornUtil.getUserId());
        u.setUsername(user.getUsername());
        u.setAvatar(user.getAvatar());
        u.setGender(user.getGender());
        u.setIntroduction(user.getIntroduction());
        updateById(u);
        User one = lambdaQuery().eq(User::getId, u.getId()).one();
        RebornCache.put(RebornUtil.getToken(), one, CommonConst.TOKEN_EXPIRE);
        RebornCache.put(CommonConst.USER_TOKEN + one.getId(), RebornUtil.getToken(), CommonConst.TOKEN_EXPIRE);

        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(one, userVO);
        userVO.setPassword(null);
        userVO.setAccessToken(RebornUtil.getToken());
        return RebornResult.success(userVO);
    }

    @Override
    public RebornResult getCode(Integer flag) {
        User user = RebornUtil.getCurrentUser();
        int i = new Random().nextInt(900000) + 100000;
        if (flag == 1) {
            if (!StringUtils.hasText(user.getPhoneNumber())) {
                return RebornResult.fail("请先绑定手机号！");
            }

            log.info(user.getId() + "---" + user.getUsername() + "---" + "手机验证码---" + i);
        } else if (flag == 2) {
            if (!StringUtils.hasText(user.getEmail())) {
                return RebornResult.fail("请先绑定邮箱！");
            }

            log.info(user.getId() + "---" + user.getUsername() + "---" + "邮箱验证码---" + i);

            List<String> mail = new ArrayList<>();
            mail.add(user.getEmail());
            String text = getCodeMail(i);
            WebInfo webInfo = (WebInfo) RebornCache.get(CommonConst.WEB_INFO);

            AtomicInteger count = (AtomicInteger) RebornCache.get(CommonConst.CODE_MAIL + mail.get(0));
            if (count == null || count.get() < CommonConst.CODE_MAIL_COUNT) {
                mailUtil.sendMailMessage(mail, "您有一封来自" + (webInfo == null ? "REBORN" : webInfo.getWebName()) + "的回执！", text);
                if (count == null) {
                    RebornCache.put(CommonConst.CODE_MAIL + mail.get(0), new AtomicInteger(1), CommonConst.CODE_EXPIRE);
                } else {
                    count.incrementAndGet();
                }
            } else {
                return RebornResult.fail("验证码发送次数过多，请明天再试！");
            }
        }
        RebornCache.put(CommonConst.USER_CODE + RebornUtil.getUserId() + "_" + flag, Integer.valueOf(i), 300);
        return RebornResult.success();
    }

    @Override
    public RebornResult getCodeForBind(String place, Integer flag) {
        int i = new Random().nextInt(900000) + 100000;
        if (flag == 1) {
            log.info(place + "---" + "手机验证码---" + i);
        } else if (flag == 2) {
            log.info(place + "---" + "邮箱验证码---" + i);
            List<String> mail = new ArrayList<>();
            mail.add(place);
            String text = getCodeMail(i);
            WebInfo webInfo = (WebInfo) RebornCache.get(CommonConst.WEB_INFO);

            AtomicInteger count = (AtomicInteger) RebornCache.get(CommonConst.CODE_MAIL + mail.get(0));
            if (count == null || count.get() < CommonConst.CODE_MAIL_COUNT) {
                mailUtil.sendMailMessage(mail, "您有一封来自" + (webInfo == null ? "REBORN" : webInfo.getWebName()) + "的回执！", text);
                if (count == null) {
                    RebornCache.put(CommonConst.CODE_MAIL + mail.get(0), new AtomicInteger(1), CommonConst.CODE_EXPIRE);
                } else {
                    count.incrementAndGet();
                }
            } else {
                return RebornResult.fail("验证码发送次数过多，请明天再试！");
            }
        }
        RebornCache.put(CommonConst.USER_CODE + RebornUtil.getUserId() + "_" + place + "_" + flag, Integer.valueOf(i), 300);
        return RebornResult.success();
    }

    @Override
    public RebornResult<UserVO> updateSecretInfo(String place, Integer flag, String code, String password) {
        password = new String(SecureUtil.aes(CommonConst.CRYPOTJS_KEY.getBytes(StandardCharsets.UTF_8)).decrypt(password));

        User user = RebornUtil.getCurrentUser();
        if ((flag == 1 || flag == 2) && !DigestUtils.md5DigestAsHex(password.getBytes()).equals(user.getPassword())) {
            return RebornResult.fail("密码错误！");
        }
        if ((flag == 1 || flag == 2) && !StringUtils.hasText(code)) {
            return RebornResult.fail("请输入验证码！");
        }
        User updateUser = new User();
        updateUser.setId(user.getId());
        if (flag == 1) {
            Integer count = lambdaQuery().eq(User::getPhoneNumber, place).count();
            if (count != 0) {
                return RebornResult.fail("手机号重复！");
            }
            Integer codeCache = (Integer) RebornCache.get(CommonConst.USER_CODE + RebornUtil.getUserId() + "_" + place + "_" + flag);
            if (codeCache != null && codeCache.intValue() == Integer.parseInt(code)) {

                RebornCache.remove(CommonConst.USER_CODE + RebornUtil.getUserId() + "_" + place + "_" + flag);

                updateUser.setPhoneNumber(place);
            } else {
                return RebornResult.fail("验证码错误！");
            }

        } else if (flag == 2) {
            Integer count = lambdaQuery().eq(User::getEmail, place).count();
            if (count != 0) {
                return RebornResult.fail("邮箱重复！");
            }
            Integer codeCache = (Integer) RebornCache.get(CommonConst.USER_CODE + RebornUtil.getUserId() + "_" + place + "_" + flag);
            if (codeCache != null && codeCache.intValue() == Integer.parseInt(code)) {

                RebornCache.remove(CommonConst.USER_CODE + RebornUtil.getUserId() + "_" + place + "_" + flag);

                updateUser.setEmail(place);
            } else {
                return RebornResult.fail("验证码错误！");
            }
        } else if (flag == 3) {
            if (DigestUtils.md5DigestAsHex(place.getBytes()).equals(user.getPassword())) {
                updateUser.setPassword(DigestUtils.md5DigestAsHex(password.getBytes()));
            } else {
                return RebornResult.fail("密码错误！");
            }
        }
        updateById(updateUser);

        User one = lambdaQuery().eq(User::getId, user.getId()).one();
        RebornCache.put(RebornUtil.getToken(), one, CommonConst.TOKEN_EXPIRE);
        RebornCache.put(CommonConst.USER_TOKEN + one.getId(), RebornUtil.getToken(), CommonConst.TOKEN_EXPIRE);

        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(one, userVO);
        userVO.setPassword(null);
        return RebornResult.success(userVO);
    }

    @Override
    public RebornResult getCodeForForgetPassword(String place, Integer flag) {
        int i = new Random().nextInt(900000) + 100000;
        if (flag == 1) {
            log.info(place + "---" + "手机验证码---" + i);
        } else if (flag == 2) {
            log.info(place + "---" + "邮箱验证码---" + i);

            List<String> mail = new ArrayList<>();
            mail.add(place);
            String text = getCodeMail(i);
            WebInfo webInfo = (WebInfo) RebornCache.get(CommonConst.WEB_INFO);

            AtomicInteger count = (AtomicInteger) RebornCache.get(CommonConst.CODE_MAIL + mail.get(0));
            if (count == null || count.get() < CommonConst.CODE_MAIL_COUNT) {
                mailUtil.sendMailMessage(mail, "您有一封来自" + (webInfo == null ? "REBORN" : webInfo.getWebName()) + "的回执！", text);
                if (count == null) {
                    RebornCache.put(CommonConst.CODE_MAIL + mail.get(0), new AtomicInteger(1), CommonConst.CODE_EXPIRE);
                } else {
                    count.incrementAndGet();
                }
            } else {
                return RebornResult.fail("验证码发送次数过多，请明天再试！");
            }
        }
        RebornCache.put(CommonConst.FORGET_PASSWORD + place + "_" + flag, Integer.valueOf(i), 300);
        return RebornResult.success();
    }

    @Override
    public RebornResult updateForForgetPassword(String place, Integer flag, String code, String password) {
        password = new String(SecureUtil.aes(CommonConst.CRYPOTJS_KEY.getBytes(StandardCharsets.UTF_8)).decrypt(password));

        Integer codeCache = (Integer) RebornCache.get(CommonConst.FORGET_PASSWORD + place + "_" + flag);
        if (codeCache == null || codeCache != Integer.parseInt(code)) {
            return RebornResult.fail("验证码错误！");
        }

        RebornCache.remove(CommonConst.FORGET_PASSWORD + place + "_" + flag);

        if (flag == 1) {
            User user = lambdaQuery().eq(User::getPhoneNumber, place).one();
            if (user == null) {
                return RebornResult.fail("该手机号未绑定账号！");
            }

            if (!user.getUserStatus()) {
                return RebornResult.fail("账号被冻结！");
            }

            lambdaUpdate().eq(User::getPhoneNumber, place).set(User::getPassword, DigestUtils.md5DigestAsHex(password.getBytes())).update();
            RebornCache.remove(CommonConst.USER_CACHE + user.getId().toString());
        } else if (flag == 2) {
            User user = lambdaQuery().eq(User::getEmail, place).one();
            if (user == null) {
                return RebornResult.fail("该邮箱未绑定账号！");
            }

            if (!user.getUserStatus()) {
                return RebornResult.fail("账号被冻结！");
            }

            lambdaUpdate().eq(User::getEmail, place).set(User::getPassword, DigestUtils.md5DigestAsHex(password.getBytes())).update();
            RebornCache.remove(CommonConst.USER_CACHE + user.getId().toString());
        }

        return RebornResult.success();
    }

    @Override
    public RebornResult<Page> listUser(BaseRequestVO baseRequestVO) {
        LambdaQueryChainWrapper<User> lambdaQuery = lambdaQuery();

        if (baseRequestVO.getUserStatus() != null) {
            lambdaQuery.eq(User::getUserStatus, baseRequestVO.getUserStatus());
        }

        if (baseRequestVO.getUserType() != null) {
            lambdaQuery.eq(User::getUserType, baseRequestVO.getUserType());
        }

        if (StringUtils.hasText(baseRequestVO.getSearchKey())) {
            lambdaQuery.and(lq -> lq.like(User::getUsername, baseRequestVO.getSearchKey())
                    .or()
                    .like(User::getPhoneNumber, baseRequestVO.getSearchKey())
                    .or()
                    .like(User::getEmail, baseRequestVO.getSearchKey()));
        }

        lambdaQuery.orderByDesc(User::getCreateTime).page(baseRequestVO);

        List<User> records = baseRequestVO.getRecords();
        if (!CollectionUtils.isEmpty(records)) {
            records.forEach(u -> {
                u.setPassword(null);
                u.setOpenId(null);
            });
        }
        return RebornResult.success(baseRequestVO);
    }

    @Override
    public RebornResult<List<UserVO>> getUserByUsername(String username) {
        List<User> users = lambdaQuery().select(User::getId, User::getUsername, User::getAvatar, User::getGender, User::getIntroduction).like(User::getUsername, username).last("limit 5").list();
        List<UserVO> userVOS = users.stream().map(u -> {
            UserVO userVO = new UserVO();
            userVO.setId(u.getId());
            userVO.setUsername(u.getUsername());
            userVO.setAvatar(u.getAvatar());
            userVO.setIntroduction(u.getIntroduction());
            userVO.setGender(u.getGender());
            return userVO;
        }).collect(Collectors.toList());
        return RebornResult.success(userVOS);
    }

    @Override
    public RebornResult<UserVO> token(String userToken) {
        userToken = new String(SecureUtil.aes(CommonConst.CRYPOTJS_KEY.getBytes(StandardCharsets.UTF_8)).decrypt(userToken));

        if (!StringUtils.hasText(userToken)) {
            throw new RebornRuntimeException("未登陆，请登陆后再进行操作！");
        }

        User user = (User) RebornCache.get(userToken);

        if (user == null) {
            throw new RebornRuntimeException("登录已过期，请重新登陆！");
        }

        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        userVO.setPassword(null);

        userVO.setAccessToken(userToken);

        return RebornResult.success(userVO);
    }

    @Override
    public RebornResult<UserVO> subscribe(Integer labelId, Boolean flag) {
        UserVO userVO = null;
        User one = lambdaQuery().eq(User::getId, RebornUtil.getUserId()).one();
        List<Integer> sub = JSON.parseArray(one.getSubscribe(), Integer.class);
        if (sub == null) sub = new ArrayList<>();
        if (flag) {
            if (!sub.contains(labelId)) {
                sub.add(labelId);
                User user = new User();
                user.setId(one.getId());
                user.setSubscribe(JSON.toJSONString(sub));
                updateById(user);

                userVO = new UserVO();
                BeanUtils.copyProperties(one, userVO);
                userVO.setPassword(null);
                userVO.setSubscribe(user.getSubscribe());
                userVO.setAccessToken(RebornUtil.getToken());
            }
        } else {
            if (sub.contains(labelId)) {
                sub.remove(labelId);
                User user = new User();
                user.setId(one.getId());
                user.setSubscribe(JSON.toJSONString(sub));
                updateById(user);

                userVO = new UserVO();
                BeanUtils.copyProperties(one, userVO);
                userVO.setPassword(null);
                userVO.setSubscribe(user.getSubscribe());
                userVO.setAccessToken(RebornUtil.getToken());
            }
        }
        return RebornResult.success(userVO);
    }

    private String getCodeMail(int i) {
        WebInfo webInfo = (WebInfo) RebornCache.get(CommonConst.WEB_INFO);
        String webName = (webInfo == null ? "REBORN" : webInfo.getWebName());
        return String.format(mailUtil.getMailText(),
                webName,
                String.format(MailUtil.imMail, RebornUtil.getAdminUser().getUsername()),
                RebornUtil.getAdminUser().getUsername(),
                String.format(codeFormat, i),
                "",
                webName);
    }
}
