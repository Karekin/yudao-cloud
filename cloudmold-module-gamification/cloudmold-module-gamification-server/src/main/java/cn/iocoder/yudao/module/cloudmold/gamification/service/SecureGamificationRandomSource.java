package cn.iocoder.yudao.module.cloudmold.gamification.service;

import cn.hutool.crypto.digest.DigestUtil;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class SecureGamificationRandomSource implements GamificationRandomSource {
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public Selection select(int totalWeight) {
        if (totalWeight <= 0) {
            throw new IllegalArgumentException("totalWeight must be positive");
        }
        byte[] entropy = new byte[32];
        secureRandom.nextBytes(entropy);
        int ticket = Math.floorMod(bytesToInt(entropy), totalWeight) + 1;
        return new Selection(ticket, DigestUtil.sha256Hex(entropy));
    }

    private static int bytesToInt(byte[] value) {
        return (value[0] & 0xff) << 24 | (value[1] & 0xff) << 16
                | (value[2] & 0xff) << 8 | (value[3] & 0xff);
    }
}
