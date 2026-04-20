package in.copmap.service;

import in.copmap.entity.Alert;
import in.copmap.entity.User;

public interface NotificationService {
    void notifyOfficer(User officer, String title, String body);
    void broadcastSOSNotification(Alert alert);
}
