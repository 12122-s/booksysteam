package com.library.entity;

/** 借阅申请（读者在线申请 → 管理员办理借出；无库存时排队预约）。 */
public class BorrowRequest {

    private Integer id;
    private Integer bookId;
    private Integer readerId;
    private String status = "待办理";   // 待办理 / 可借 / 已办理 / 已取消
    private String note;
    private String createdAt;
    private String doneAt;
    private String readyAt;
    private Integer notified = 0;

    // ---- 联查字段 ----
    private String bookTitle;
    private String bookAuthor;
    private Integer availableCopies;
    private String location;
    private String readerName;
    private String cardNo;
    private String phone;
    private String email;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Integer getBookId() { return bookId; }
    public void setBookId(Integer bookId) { this.bookId = bookId; }
    public Integer getReaderId() { return readerId; }
    public void setReaderId(Integer readerId) { this.readerId = readerId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getDoneAt() { return doneAt; }
    public void setDoneAt(String doneAt) { this.doneAt = doneAt; }
    public String getReadyAt() { return readyAt; }
    public void setReadyAt(String readyAt) { this.readyAt = readyAt; }
    public Integer getNotified() { return notified; }
    public void setNotified(Integer notified) { this.notified = notified; }
    public String getBookTitle() { return bookTitle; }
    public void setBookTitle(String bookTitle) { this.bookTitle = bookTitle; }
    public String getBookAuthor() { return bookAuthor; }
    public void setBookAuthor(String bookAuthor) { this.bookAuthor = bookAuthor; }
    public Integer getAvailableCopies() { return availableCopies; }
    public void setAvailableCopies(Integer availableCopies) { this.availableCopies = availableCopies; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getReaderName() { return readerName; }
    public void setReaderName(String readerName) { this.readerName = readerName; }
    public String getCardNo() { return cardNo; }
    public void setCardNo(String cardNo) { this.cardNo = cardNo; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
