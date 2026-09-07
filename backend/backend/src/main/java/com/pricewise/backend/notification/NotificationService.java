package com.pricewise.backend.notification;

import com.pricewise.backend.entity.TrackedProduct;

public interface NotificationService {
    void sendPriceDropAlert(TrackedProduct trackedProduct, double previousPrice, double newPrice, double dropAmount, double dropPercentage);
}
