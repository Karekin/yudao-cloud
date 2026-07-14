package cn.iocoder.yudao.module.cloudmold.datacontract.service.inbox;

public record InboxExecutionResult<T>(boolean duplicate, T value, String resultHash) {
}
