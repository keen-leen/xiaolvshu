package com.xiaolvshu.service;

import com.xiaolvshu.mapper.TagMapper;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TagServiceTest {

    @Test
    void shouldAdjustUseCountAtomically() {
        TagMapper tagMapper = mock(TagMapper.class);
        TagService service = new TagService(tagMapper);

        service.incrementUseCount(10);
        service.decrementUseCount(10);

        verify(tagMapper).adjustUseCount(10, 1);
        verify(tagMapper).adjustUseCount(10, -1);
    }
}
