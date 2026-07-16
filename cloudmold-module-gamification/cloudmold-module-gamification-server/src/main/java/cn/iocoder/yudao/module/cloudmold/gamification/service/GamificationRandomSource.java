package cn.iocoder.yudao.module.cloudmold.gamification.service;

public interface GamificationRandomSource {
    Selection select(int totalWeight);

    record Selection(int ticket, String entropySha256) {
    }
}
