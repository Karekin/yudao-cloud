package cn.iocoder.yudao.module.bpm.api.definition;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.bpm.api.definition.dto.BpmSystemModelRegisterReqDTO;
import cn.iocoder.yudao.module.bpm.enums.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = ApiConstants.NAME)
@Tag(name = "RPC 服务 - 系统流程模型")
public interface BpmSystemModelApi {

    String PREFIX = ApiConstants.PREFIX + "/system-model";

    @PostMapping(PREFIX + "/register")
    @Operation(summary = "幂等登记并发布由业务模块维护的系统流程模型")
    CommonResult<String> register(@Valid @RequestBody BpmSystemModelRegisterReqDTO reqDTO);

}
