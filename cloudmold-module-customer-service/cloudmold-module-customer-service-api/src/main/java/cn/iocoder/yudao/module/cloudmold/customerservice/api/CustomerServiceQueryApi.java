package cn.iocoder.yudao.module.cloudmold.customerservice.api;

public interface CustomerServiceQueryApi {
    CustomerServiceView getTicket(String ticketId);
    CustomerServiceView getClaim(String claimId);
}
