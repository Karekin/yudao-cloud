package cn.iocoder.yudao.module.cloudmold.crm.api.contract;

import java.util.List;

public interface SalesContractQueryApi {

    SalesContractView get(String salesContractId);

    List<SalesContractView> list();
}
