package com.hdg.prysm.context;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PrContextTest {

    /**
     * 有效参数应原样保存在 PR 上下文中。
     */
    @Test
    void shouldCreatePrContextWithValidValues() {
        PrContext context = new PrContext("chinensdkcsdck", "PRysm", 3);

        assertEquals("chinensdkcsdck", context.getOwner());
        assertEquals("PRysm", context.getRepository());
        assertEquals(3, context.getPullRequestNumber());
        assertEquals("chinensdkcsdck/PRysm", context.fullRepositoryName());
    }

    /**
     * 仓库所属账号为空时，不应创建上下文。
     */
    @Test
    void shouldRejectBlankOwner() {
        assertThrows(IllegalArgumentException.class, () -> new PrContext(" ", "PRysm", 3));
    }

    /**
     * 仓库名为空时，不应创建上下文。
     */
    @Test
    void shouldRejectBlankRepository() {
        assertThrows(IllegalArgumentException.class, () -> new PrContext("chinensdkcsdck", " ", 3));
    }

    /**
     * PR 编号必须是正整数。
     */
    @Test
    void shouldRejectNonPositivePullRequestNumber() {
        assertThrows(IllegalArgumentException.class, () -> new PrContext("chinensdkcsdck", "PRysm", 0));
    }
}
