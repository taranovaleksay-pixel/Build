package com.example.coursach.models;
public class Profile {
    private String id,email,firstName,lastName,role,createdAt; private boolean isBlocked;
    public String getId(){return id;} public void setId(String v){id=v;}
    public String getEmail(){return email;} public void setEmail(String v){email=v;}
    public String getFirstName(){return firstName;} public void setFirstName(String v){firstName=v;}
    public String getLastName(){return lastName;} public void setLastName(String v){lastName=v;}
    public String getRole(){return role;} public void setRole(String v){role=v;}
    public boolean isBlocked(){return isBlocked;} public void setBlocked(boolean v){isBlocked=v;}
    public String getCreatedAt(){return createdAt;} public void setCreatedAt(String v){createdAt=v;}
    public String getFullName(){return (firstName+" "+lastName).trim();}
    public String getRoleLabel(){
        if(role==null)return "Клиент";
        switch(role){case "admin":return "Администратор";case "manager":return "Менеджер";default:return "Клиент";}
    }
}
