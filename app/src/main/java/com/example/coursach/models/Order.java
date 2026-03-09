package com.example.coursach.models;
public class Order {
    private String id,buyerId,sellerId,status,createdAt,buyerName,sellerName,serviceTitle;
    private double totalAmount;
    public String getId(){return id;} public void setId(String v){id=v;}
    public String getBuyerId(){return buyerId;} public void setBuyerId(String v){buyerId=v;}
    public String getSellerId(){return sellerId;} public void setSellerId(String v){sellerId=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public String getCreatedAt(){return createdAt;} public void setCreatedAt(String v){createdAt=v;}
    public String getBuyerName(){return buyerName;} public void setBuyerName(String v){buyerName=v;}
    public String getSellerName(){return sellerName;} public void setSellerName(String v){sellerName=v;}
    public String getServiceTitle(){return serviceTitle;} public void setServiceTitle(String v){serviceTitle=v;}
    public double getTotalAmount(){return totalAmount;} public void setTotalAmount(double v){totalAmount=v;}
    public String getStatusLabel(){
        if(status==null)return "—";
        switch(status){case "pending":return "Ожидает";case "confirmed":return "Подтверждён";
            case "in_progress":return "Выполняется";case "completed":return "Завершён";
            case "cancelled":return "Отменён";default:return status;}
    }
}
