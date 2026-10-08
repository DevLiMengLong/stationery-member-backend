package com.gechuang.stationery.notification;

import com.gechuang.stationery.mainflow.MainFlowMapper;
import org.springframework.stereotype.Service;

@Service
public class SmsQuotaService {

    private final MainFlowMapper mapper;

    public SmsQuotaService(MainFlowMapper mapper) {
        this.mapper = mapper;
    }

    public boolean reserve(Long storeId) {
        return storeId != null && mapper.tryConsumeSmsQuota(storeId) > 0;
    }

    public void release(Long storeId) {
        if (storeId != null) {
            mapper.restoreSmsQuota(storeId);
        }
    }
}
