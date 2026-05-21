package com.example.coursach.utils;

import java.util.ArrayList;
import java.util.List;
public class BuildHubUtils {
    public static class ServiceModel {

        public String title, description, category;
        public double price;
        public ServiceModel(String title, String description, String category, double price) {
            this.title = title;
            this.description = description;
            this.category = category;
            this.price = price;
        }
    }


    public static List<ServiceModel> filter(List<ServiceModel> all, String query, String category) {
        String q = query == null ? "" : query.toLowerCase().trim();
        List<ServiceModel> result = new ArrayList<>();
        for (ServiceModel s : all) {
            boolean matchCat = category == null || category.equals("Все") || s.category.equals(category);
            boolean matchQ = q.isEmpty() || s.title.toLowerCase().contains(q) || s.description.toLowerCase().contains(q);
            if (matchCat && matchQ) result.add(s);
        }
        return result;
    }
    public static String validateOrder(String userId, String serviceId, double price) {
        if (userId == null || userId.isEmpty()) return "Войдите в аккаунт";
        if (serviceId == null || serviceId.isEmpty()) return "Услуга не выбрана";
        if (price <= 0) return "Некорректная цена услуги";
        return null;
    }
    public static String getStatusLabel(String status) {
        if (status == null) return "Неизвестно";
        switch (status) {
            case "pending":   return "Ожидает";
            case "active":    return "В работе";
            case "completed": return "Завершён";
            case "cancelled": return "Отменён";
            default:          return "Неизвестно";
        }
    }
    public static String maskCard(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 4) return "****";
        String digits = cardNumber.replaceAll("\\s", "");
        String last4 = digits.substring(digits.length() - 4);
        return "**** **** **** " + last4;
    }
    public static class UserModel {
        public String name;
        public UserModel(String name) { this.name = name; }
    }

    public static List<UserModel> filterUsers(List<UserModel> users, String query) {
        String q = query == null ? "" : query.toLowerCase().trim();
        List<UserModel> result = new ArrayList<>();
        for (UserModel u : users) {
            if (q.isEmpty() || u.name.toLowerCase().contains(q)) result.add(u);
        }
        return result;
    }
}