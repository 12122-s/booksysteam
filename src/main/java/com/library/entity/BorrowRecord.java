package com.library.entity;

/**
 * 借阅记录。
 *
 * <p>除了表里的原始字段，还带两类附加字段：
 * 1) 联查字段（bookTitle / readerName 等，来自 SQL 的 JOIN）；
 * 2) 计算字段（status / overdueDays / fine / daysLeft），由 Service 统一填充，
 *    规则见 BorrowService#decorate。
 */
public class BorrowRecord {

    private Integer id;
    private Integer bookId;
    private Integer readerId;
    private String borrowDate;
    private String dueDate;
    private String returnDate;
    private Integer renewCount = 0;
    private Double fineAmount = 0.0;
    private String source = "管理员";
    private String remark;
    private Integer finePaid = 0;
    private String finePaidAt;
    private String fineNote;
    private String fineClaim = "";
    private String fineClaimAt;

    // ---- 联查字段 ----
    private String bookTitle;
    private String bookAuthor;
    private String bookIsbn;
    private String bookLocation;
    private String readerName;
    private String cardNo;
    private String phone;
    private String department;

    // ---- 计算字段（decorate 填充） ----
    private String status;        // 已归还 / 借出中 / 已逾期
    private Integer overdueDays = 0;
    private Double fine = 0.0;
    private Integer daysLeft;     // 未归还时：距应还日还有几天

    public boolean getFinePaidFlag() {
        return finePaid != null && finePaid == 1;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Integer getBookId() { return bookId; }
    public void setBookId(Integer bookId) { this.bookId = bookId; }
    public Integer getReaderId() { return readerId; }
    public void setReaderId(Integer readerId) { this.readerId = readerId; }
    public String getBorrowDate() { return borrowDate; }
    public void setBorrowDate(String borrowDate) { this.borrowDate = borrowDate; }
    public String getDueDate() { return dueDate; }
    public void setDueDate(String dueDate) { this.dueDate = dueDate; }
    public String getReturnDate() { return returnDate; }
    public void setReturnDate(String returnDate) { this.returnDate = returnDate; }
    public Integer getRenewCount() { return renewCount; }
    public void setRenewCount(Integer renewCount) { this.renewCount = renewCount; }
    public Double getFineAmount() { return fineAmount; }
    public void setFineAmount(Double fineAmount) { this.fineAmount = fineAmount; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Integer getFinePaid() { return finePaid; }
    public void setFinePaid(Integer finePaid) { this.finePaid = finePaid; }
    public String getFinePaidAt() { return finePaidAt; }
    public void setFinePaidAt(String finePaidAt) { this.finePaidAt = finePaidAt; }
    public String getFineNote() { return fineNote; }
    public void setFineNote(String fineNote) { this.fineNote = fineNote; }
    public String getFineClaim() { return fineClaim; }
    public void setFineClaim(String fineClaim) { this.fineClaim = fineClaim; }
    public String getFineClaimAt() { return fineClaimAt; }
    public void setFineClaimAt(String fineClaimAt) { this.fineClaimAt = fineClaimAt; }
    public String getBookTitle() { return bookTitle; }
    public void setBookTitle(String bookTitle) { this.bookTitle = bookTitle; }
    public String getBookAuthor() { return bookAuthor; }
    public void setBookAuthor(String bookAuthor) { this.bookAuthor = bookAuthor; }
    public String getBookIsbn() { return bookIsbn; }
    public void setBookIsbn(String bookIsbn) { this.bookIsbn = bookIsbn; }
    public String getBookLocation() { return bookLocation; }
    public void setBookLocation(String bookLocation) { this.bookLocation = bookLocation; }
    public String getReaderName() { return readerName; }
    public void setReaderName(String readerName) { this.readerName = readerName; }
    public String getCardNo() { return cardNo; }
    public void setCardNo(String cardNo) { this.cardNo = cardNo; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getOverdueDays() { return overdueDays; }
    public void setOverdueDays(Integer overdueDays) { this.overdueDays = overdueDays; }
    public Double getFine() { return fine; }
    public void setFine(Double fine) { this.fine = fine; }
    public Integer getDaysLeft() { return daysLeft; }
    public void setDaysLeft(Integer daysLeft) { this.daysLeft = daysLeft; }
}
