package cn.iocoder.yudao.module.cloudmold.customerservice.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppCustomerServicePageView {
    private List<CustomerServiceView> list;
    private long total;
    private int pageNo;
    private int pageSize;
}
