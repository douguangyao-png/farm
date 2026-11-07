package cn.iocoder.yudao.module.farm.service;

import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 业务单号生成：前缀 + yyyyMMdd + 四位当日序号（Redis 自增，按「前缀+日期」分键，不会重复）。
 * 对应 mock 的 makeNo（mock-server/src/farm/common.ts），序号由 3 位改为 4 位。
 */
@Component
public class FarmNoGenerator {

    @Resource
    private StringRedisTemplate redisTemplate;

    public String next(String prefix) {
        String ymd = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String key = "farm:no:" + prefix + ":" + ymd;
        Long n = redisTemplate.opsForValue().increment(key);
        if (n == null) throw new IllegalStateException("单号生成失败：Redis 自增无返回");
        if (n == 1L) redisTemplate.expire(key, Duration.ofDays(2));
        return prefix + ymd + String.format("%04d", n);
    }
}
