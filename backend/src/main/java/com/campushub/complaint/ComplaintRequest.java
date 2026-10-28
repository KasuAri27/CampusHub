package com.campushub.complaint;

import lombok.Data;

@Data
public class ComplaintRequest {
    private Long orderId;
    private Long targetId;
    private String reason;
    private String description;
    private String imageUrl;
}
