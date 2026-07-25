package cn.iocoder.yudao.module.cloudmold.catalog.api;

public interface CatalogCommandApi {

    DefineCatalogSkuResult defineSku(DefineCatalogSkuCommand command);

    CatalogMetadataUpdateResult updateMetadata(CatalogMetadataUpdateCommand command);

    CatalogBarcodeRotateResult rotateBarcode(CatalogBarcodeRotateCommand command);

    CatalogLifecycleResult changeStatus(CatalogLifecycleCommand command);

}
