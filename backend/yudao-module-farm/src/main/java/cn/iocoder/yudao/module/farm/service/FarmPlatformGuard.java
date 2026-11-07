package cn.iocoder.yudao.module.farm.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.FORBIDDEN;

/** 平台租户（id=1）判断。平台可以看全部租户，经营者只能看自己租户 */
public final class FarmPlatformGuard {

    public static final Long PLATFORM_TENANT_ID = 1L;

    private FarmPlatformGuard() {
    }

    public static boolean isPlatform() {
        return PLATFORM_TENANT_ID.equals(TenantContextHolder.getRequiredTenantId());
    }

    /** 非平台调用抛 403 */
    public static void requirePlatform() {
        if (!isPlatform()) throw exception(FORBIDDEN, "只有平台管理员可以操作");
    }
}
