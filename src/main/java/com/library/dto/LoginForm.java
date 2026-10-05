package com.library.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 统一登录表单。字段名与页面 input 保持一致，格式类校验用 Bean Validation 注解，
 * 业务类判断（管理员/读者分流、密码比对）在控制器完成。
 */
public class LoginForm {

    /** 管理员填管理员账号，读者填借书卡号或手机号。 */
    @NotBlank(message = "请填写账号：管理员填管理员账号，读者填借书卡号或手机号。")
    private String account;

    @NotBlank(message = "请填写密码")
    private String password;

    public String getAccount() { return account; }
    public void setAccount(String account) { this.account = account; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
