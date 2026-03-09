package com.example.coursach.models;
public class Review {
    private String id,serviceId,authorId,authorName,comment,createdAt; private int rating;
    public String getId(){return id;} public void setId(String v){id=v;}
    public String getServiceId(){return serviceId;} public void setServiceId(String v){serviceId=v;}
    public String getAuthorId(){return authorId;} public void setAuthorId(String v){authorId=v;}
    public String getAuthorName(){return authorName;} public void setAuthorName(String v){authorName=v;}
    public String getComment(){return comment;} public void setComment(String v){comment=v;}
    public int getRating(){return rating;} public void setRating(int v){rating=v;}
    public String getCreatedAt(){return createdAt;} public void setCreatedAt(String v){createdAt=v;}
}
