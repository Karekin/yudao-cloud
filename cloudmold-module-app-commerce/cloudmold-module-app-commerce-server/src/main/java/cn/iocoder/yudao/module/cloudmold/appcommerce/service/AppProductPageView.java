package cn.iocoder.yudao.module.cloudmold.appcommerce.service;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class AppProductPageView {
    List<AppProductView> list;
    Long total;
    Integer pageNo;
    Integer pageSize;
}
