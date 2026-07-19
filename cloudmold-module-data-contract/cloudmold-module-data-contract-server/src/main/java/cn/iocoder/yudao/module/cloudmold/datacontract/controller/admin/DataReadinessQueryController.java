package cn.iocoder.yudao.module.cloudmold.datacontract.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.cloudmold.datacontract.controller.admin.vo.DataReadinessOverviewRespVO;
import cn.iocoder.yudao.module.cloudmold.datacontract.service.query.DataReadinessQueryService;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/cloudmold/data-readiness")
public class DataReadinessQueryController {

    @Resource
    private DataReadinessQueryService dataReadinessQueryService;

    @GetMapping("/overview")
    @PreAuthorize("@ss.hasPermission('cloudmold:data-readiness:query')")
    public CommonResult<DataReadinessOverviewRespVO> getOverview() {
        return success(dataReadinessQueryService.getOverview());
    }
}
