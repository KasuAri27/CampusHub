package com.campushub.admin;

import lombok.Data;

@Data
public class CreditAdjustRequest {
    private int delta;

    /** 可选的客户端操作编号，用作信用分调整的幂等键；缺省由服务端生成。 */
    private String operationId;
}
