package com.hdg.prysm.diff;

import com.hdg.prysm.context.PrContext;

/**
 * Pull Request diff 获取边界。
 *
 * PR4 提供实现，后续 review 流程只依赖该接口消费 diff。
 */
public interface PrDiffProvider {

    /**
     * 根据 Pull Request 上下文获取 diff。
     */
    PrDiff fetch(PrContext context);
}
