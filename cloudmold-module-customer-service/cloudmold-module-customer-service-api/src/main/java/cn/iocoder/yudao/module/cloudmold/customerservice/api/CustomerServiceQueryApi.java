package cn.iocoder.yudao.module.cloudmold.customerservice.api;

public interface CustomerServiceQueryApi {
    CustomerServiceView getTicket(String ticketId);
    CustomerServiceView requireOwnedTicket(String ticketId, String customerPrincipalId);
    AppCustomerServicePageView listOwnedTickets(String customerPrincipalId, int pageNo, int pageSize);
    CustomerServiceView getClaim(String claimId);
}
