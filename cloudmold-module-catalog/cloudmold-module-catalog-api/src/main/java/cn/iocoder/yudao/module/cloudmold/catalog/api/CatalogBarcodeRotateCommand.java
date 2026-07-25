package cn.iocoder.yudao.module.cloudmold.catalog.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CatalogBarcodeRotateCommand {
    private String skuId;
    private Long expectedVersion;
    private String idempotencyKey;
    private String reason;
    private String barcode;
    private String barcodeType;
    private String correlationId;
    private String causationId;
    private Instant occurredAt;
}
