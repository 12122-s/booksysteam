package com.library.entity;

/** 读者。 */
public class Reader {

    private Integer id;
    private String cardNo;
    private String name;
    private String gender;
    private String readerType;
    private String department;
    private String phone;
    private String email;
    private String passwordHash;
    private String status = "正常";
    private Integer isDeleted = 0;
    private String source = "管理员登记";
    private String createdAt;
    /** 列表页联查字段：当前在借册数。 */
    private Integer borrowing = 0;
    /** 列表页联查字段：当前逾期册数。 */
    private Integer overdueCount = 0;

    public boolean getDeleted() {
        return isDeleted != null && isDeleted == 1;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getCardNo() { return cardNo; }
    public void setCardNo(String cardNo) { this.cardNo = cardNo; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public String getReaderType() { return readerType; }
    public void setReaderType(String readerType) { this.readerType = readerType; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Integer isDeleted) { this.isDeleted = isDeleted; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public Integer getBorrowing() { return borrowing; }
    public void setBorrowing(Integer borrowing) { this.borrowing = borrowing; }
    public Integer getOverdueCount() { return overdueCount; }
    public void setOverdueCount(Integer overdueCount) { this.overdueCount = overdueCount; }
}
