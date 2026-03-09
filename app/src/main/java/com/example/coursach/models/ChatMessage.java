package com.example.coursach.models;
public class ChatMessage {
    private String id,orderId,senderId,senderName,content,createdAt; private boolean isMe;
    public String getId(){return id;} public void setId(String v){id=v;}
    public String getOrderId(){return orderId;} public void setOrderId(String v){orderId=v;}
    public String getSenderId(){return senderId;} public void setSenderId(String v){senderId=v;}
    public String getSenderName(){return senderName;} public void setSenderName(String v){senderName=v;}
    public String getContent(){return content;} public void setContent(String v){content=v;}
    public String getCreatedAt(){return createdAt;} public void setCreatedAt(String v){createdAt=v;}
    public boolean isMe(){return isMe;} public void setMe(boolean v){isMe=v;}
}
