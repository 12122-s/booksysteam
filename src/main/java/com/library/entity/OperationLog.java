package com.library.entity;

/** 操作日志（谁、什么时候、干了什么）。 */
public class OperationLog {

    private Integer id;
    private String createdAt;
    private String who;
    private String role;
    private String action;
    private String target;
    private String detail;
    private String ip;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getWho() { return who; }
    public void setWho(String who) { this.who = who; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }
}
