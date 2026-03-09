package com.example.coursach.models;
public class Service {
    private String id, sellerId, sellerName, title, description, category, createdAt;
    private double price; private float rating; private int reviewCount;
    public String getId(){return id;} public void setId(String v){id=v;}
    public String getSellerId(){return sellerId;} public void setSellerId(String v){sellerId=v;}
    public String getSellerName(){return sellerName;} public void setSellerName(String v){sellerName=v;}
    public String getTitle(){return title;} public void setTitle(String v){title=v;}
    public String getDescription(){return description;} public void setDescription(String v){description=v;}
    public String getCategory(){return category;} public void setCategory(String v){category=v;}
    public double getPrice(){return price;} public void setPrice(double v){price=v;}
    public float getRating(){return rating;} public void setRating(float v){rating=v;}
    public int getReviewCount(){return reviewCount;} public void setReviewCount(int v){reviewCount=v;}
    public String getCreatedAt(){return createdAt;} public void setCreatedAt(String v){createdAt=v;}
}
