package com.qingluo.link.api.stp;

import com.qingluo.link.mapper.SysUserMapper;
import com.qingluo.link.model.dto.entity.SysUser;
import com.qingluo.link.service.config.StpInterfaceImpl;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StpInterfaceImplTest {

    @Mock private SysUserMapper sysUserMapper;
    @InjectMocks private StpInterfaceImpl stpInterface;

    @Test
    void roleList_readsCurrentDatabaseRole() {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setRole("ADMIN");
        given(sysUserMapper.selectById(1L)).willReturn(user);

        assertThat(stpInterface.getRoleList("1", "login")).containsExactly("ADMIN");
        verify(sysUserMapper).selectById(1L);
    }

    @Test
    void roleList_returnsEmptyForMissingOrInvalidUser() {
        given(sysUserMapper.selectById(99L)).willReturn(null);
        assertThat(stpInterface.getRoleList(99L, "login")).isEmpty();
        assertThat(stpInterface.getRoleList("bad", "login")).isEmpty();
    }

    @Test
    void permissionList_remainsEmpty() {
        List<String> permissions = stpInterface.getPermissionList(1L, "login");
        assertThat(permissions).isEmpty();
    }
}
