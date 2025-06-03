package com.ld.reborn.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ld.reborn.config.RebornResult;
import com.ld.reborn.entity.User;
import com.ld.reborn.vo.BaseRequestVO;
import com.ld.reborn.vo.UserVO;

import java.util.List;

/**
 * <p>
 * 用户信息表 服务类
 * </p>
 */
public interface UserService extends IService<User> {

    /**
     * 用户名、邮箱、手机号/密码登录
     *
     * @param account
     * @param password
     * @return
     */
    RebornResult<UserVO> login(String account, String password, Boolean isAdmin);

    RebornResult exit();

    RebornResult<UserVO> regist(UserVO user);

    RebornResult<UserVO> updateUserInfo(UserVO user);

    RebornResult getCode(Integer flag);

    RebornResult getCodeForBind(String place, Integer flag);

    RebornResult<UserVO> updateSecretInfo(String place, Integer flag, String code, String password);

    RebornResult getCodeForForgetPassword(String place, Integer flag);

    RebornResult updateForForgetPassword(String place, Integer flag, String code, String password);

    RebornResult<Page> listUser(BaseRequestVO baseRequestVO);

    RebornResult<List<UserVO>> getUserByUsername(String username);

    RebornResult<UserVO> token(String userToken);

    RebornResult<UserVO> subscribe(Integer labelId, Boolean flag);
}
