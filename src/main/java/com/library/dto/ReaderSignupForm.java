package com.library.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 读者自助办卡表单（公开入口，安全敏感，格式校验用注解锁死）。
 * 只绑定无下划线字段；confirm_password、reader_type、department、email、agree
 * 仍由控制器用参数接收（业务规则：两次密码一致、同意条款等不属于格式校验）。
 */
public class ReaderSignupForm {

    @NotBlank(message = "请填写姓名")
    private String name;

    /** 手机号：必填、纯数字、6~20 位，用于登录和到期提醒。 */
    @NotBlank(message = "手机号请填写数字（例如 13800000000），方便图书室联系你")
    @Pattern(regexp = "^\\d{6,20}$",
            message = "手机号请填写数字（例如 13800000000），方便图书室联系你")
    private String phone;

    @NotNull(message = "密码至少要 6 位")
    @Size(min = 6, message = "密码至少要 6 位")
    private String password;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
