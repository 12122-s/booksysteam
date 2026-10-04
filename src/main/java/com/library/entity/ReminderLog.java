package com.library.entity;

/** 提醒邮件发送记录。 */
public class ReminderLog {

    private Integer id;
    private Integer borrowId;
    private String kind;      // due_soon / overdue
    private String sentTo;
    private Integer ok = 1;
    private String message;
    private String sentAt;

    // ---- 联查字段 ----
    private String bookTitle;
    private String readerName;
    private String dueDate;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Integer getBorrowId() { return borrowId; }
    public void setBorrowId(Integer borrowId) { this.borrowId = borrowId; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getSentTo() { return sentTo; }
    public void setSentTo(String sentTo) { this.sentTo = sentTo; }
    public Integer getOk() { return ok; }
    public void setOk(Integer ok) { this.ok = ok; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getSentAt() { return sentAt; }
    public void setSentAt(String sentAt) { this.sentAt = sentAt; }
    public String getBookTitle() { return bookTitle; }
    public void setBookTitle(String bookTitle) { this.bookTitle = bookTitle; }
    public String getReaderName() { return readerName; }
    public void setReaderName(String readerName) { this.readerName = readerName; }
    public String getDueDate() { return dueDate; }
    public void setDueDate(String dueDate) { this.dueDate = dueDate; }
}
