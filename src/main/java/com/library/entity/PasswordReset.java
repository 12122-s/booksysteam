package com.library.entity;

/** 密码重置验证码记录。 */
public class PasswordReset {

    private Integer id;
    private Integer readerId;
    private String code;
    private String expireAt;
    private Integer used = 0;
    private Integer attempts = 0;
    private String createdAt;

    // ---- 联查字段 ----
    private String readerName;
    private String cardNo;
    private String phone;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Integer getReaderId() { return readerId; }
    public void setReaderId(Integer readerId) { this.readerId = readerId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getExpireAt() { return expireAt; }
    public void setExpireAt(String expireAt) { this.expireAt = expireAt; }
    public Integer getUsed() { return used; }
    public void setUsed(Integer used) { this.used = used; }
    public Integer getAttempts() { return attempts; }
    public void setAttempts(Integer attempts) { this.attempts = attempts; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getReaderName() { return readerName; }
    public void setReaderName(String readerName) { this.readerName = readerName; }
    public String getCardNo() { return cardNo; }
    public void setCardNo(String cardNo) { this.cardNo = cardNo; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
}
