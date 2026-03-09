package com.example.coursach.utils;

import org.junit.Before;
import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class BuildHubUnitTest {

    private List<BuildHubUtils.ServiceModel> services;
    private List<BuildHubUtils.UserModel> users;

    @Before
    public void setUp() {
        services = Arrays.asList(
                new BuildHubUtils.ServiceModel("Заливка фундамента", "Ленточный фундамент", "Фундамент", 50000),
                new BuildHubUtils.ServiceModel("Кровельные работы", "Монтаж металлочерепицы", "Кровля", 30000),
                new BuildHubUtils.ServiceModel("Ремонт кровли", "Устранение протечек", "Кровля", 15000),
                new BuildHubUtils.ServiceModel("Отделка стен", "Штукатурка и покраска", "Отделка", 20000),
                new BuildHubUtils.ServiceModel("Утепление фасада", "Минвата и штукатурный слой", "Отделка", 25000)
        );
        users = Arrays.asList(
                new BuildHubUtils.UserModel("Иван Петров"),
                new BuildHubUtils.UserModel("Мария Сидорова"),
                new BuildHubUtils.UserModel("Алексей Иванов")
        );
    }
    @Test
    public void filterByCategory_returnsOnlyMatchingServices() {
        List<BuildHubUtils.ServiceModel> result = BuildHubUtils.filter(services, "", "Кровля");
        Assert.assertEquals(2, result.size());
        for (BuildHubUtils.ServiceModel s : result) Assert.assertEquals("Кровля", s.category);
    }

    @Test
    public void filterByQuery_findsInTitleAndDescription() {
        List<BuildHubUtils.ServiceModel> result = BuildHubUtils.filter(services, "фундамент", "Все");
        Assert.assertEquals(1, result.size());
        Assert.assertEquals("Заливка фундамента", result.get(0).title);
    }

    @Test
    public void filterAllCategory_returnsAllServices() {
        List<BuildHubUtils.ServiceModel> result = BuildHubUtils.filter(services, "", "Все");
        Assert.assertEquals(5, result.size());
    }

    @Test
    public void filterUnknownQuery_returnsEmptyList() {
        List<BuildHubUtils.ServiceModel> result = BuildHubUtils.filter(services, "бассейн", "Все");
        Assert.assertTrue(result.isEmpty());
    }

    @Test
    public void filterCombinedCategoryAndQuery_returnsCorrectResult() {
        List<BuildHubUtils.ServiceModel> result = BuildHubUtils.filter(services, "ремонт", "Кровля");
        Assert.assertEquals(1, result.size());
        Assert.assertEquals("Ремонт кровли", result.get(0).title);
    }


    @Test
    public void validateOrder_validData_returnsNull() {
        Assert.assertNull(BuildHubUtils.validateOrder("user-123", "service-456", 5000));
    }

    @Test
    public void validateOrder_emptyUserId_returnsAuthError() {
        Assert.assertEquals("Войдите в аккаунт", BuildHubUtils.validateOrder("", "service-456", 5000));
    }

    @Test
    public void validateOrder_zeroPrice_returnsPriceError() {
        Assert.assertEquals("Некорректная цена услуги", BuildHubUtils.validateOrder("user-123", "service-456", 0));
    }


    @Test
    public void getStatusLabel_pending_returnsOzhidaet() {
        Assert.assertEquals("Ожидает", BuildHubUtils.getStatusLabel("pending"));
    }

    @Test
    public void getStatusLabel_completed_returnsZavershen() {
        Assert.assertEquals("Завершён", BuildHubUtils.getStatusLabel("completed"));
    }


    @Test
    public void maskCard_16digits_returnsMasked() {
        Assert.assertEquals("**** **** **** 7890", BuildHubUtils.maskCard("1234 5678 9012 7890"));
    }
}