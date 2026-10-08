package com.library.entity;

/** 图书。日期/时间字段在数据库里按字符串存储，如 "YYYY-MM-DD HH:mm:ss"。 */
public class Book {

    private Integer id;
    private String title;
    private String author;
    private String category;
    private String isbn;
    private String publisher;
    private Integer publishYear;
    private Double price;
    private String location;
    private Integer totalCopies = 1;
    private Integer availableCopies = 1;
    private Integer isDeleted = 0;
    private String summary;
    private String cover;
    private String createdAt;
    /** 列表页联查字段：当前借出未还的册数。 */
    private Integer outCount = 0;
    /** 推荐模块联查字段：全馆累计借阅次数。 */
    private Integer hot = 0;

    public boolean getDeleted() {
        return isDeleted != null && isDeleted == 1;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getIsbn() { return isbn; }
    public void setIsbn(String isbn) { this.isbn = isbn; }
    public String getPublisher() { return publisher; }
    public void setPublisher(String publisher) { this.publisher = publisher; }
    public Integer getPublishYear() { return publishYear; }
    public void setPublishYear(Integer publishYear) { this.publishYear = publishYear; }
    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public Integer getTotalCopies() { return totalCopies; }
    public void setTotalCopies(Integer totalCopies) { this.totalCopies = totalCopies; }
    public Integer getAvailableCopies() { return availableCopies; }
    public void setAvailableCopies(Integer availableCopies) { this.availableCopies = availableCopies; }
    public Integer getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Integer isDeleted) { this.isDeleted = isDeleted; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getCover() { return cover; }
    public void setCover(String cover) { this.cover = cover; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public Integer getOutCount() { return outCount; }
    public void setOutCount(Integer outCount) { this.outCount = outCount; }
    public Integer getHot() { return hot; }
    public void setHot(Integer hot) { this.hot = hot; }
}
