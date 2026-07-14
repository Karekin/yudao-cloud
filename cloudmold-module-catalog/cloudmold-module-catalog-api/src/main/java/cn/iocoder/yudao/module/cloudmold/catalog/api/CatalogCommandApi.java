package cn.iocoder.yudao.module.cloudmold.catalog.api;

public interface CatalogCommandApi {

    DefineCatalogSkuResult defineSku(DefineCatalogSkuCommand command);

    CatalogLifecycleResult changeStatus(CatalogLifecycleCommand command);

}
