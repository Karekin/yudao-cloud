package cn.iocoder.yudao.module.cloudmold.crm.service.contract;

import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEventListener;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SalesContractApprovalStatusListener extends BpmProcessInstanceStatusEventListener {

    private static final String PROCESS_DEFINITION_KEY = "cloudmold_sales_contract_approval";

    private final SalesContractApprovalStatusService statusService;

    @Override
    protected String getProcessDefinitionKey() {
        return PROCESS_DEFINITION_KEY;
    }

    @Override
    protected void onEvent(BpmProcessInstanceStatusEvent event) {
        statusService.complete(event);
    }
}
