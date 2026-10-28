package com.campushub.task;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 活动组队 — 继承自 Task，映射到 {@code t_task_team} 表.
 */
@Entity
@Table(name = "t_task_team")
@DiscriminatorValue("TEAM")
@PrimaryKeyJoinColumn(name = "task_id")
@Getter
@Setter
@NoArgsConstructor
public class TeamingTask extends Task {

    /** 成团人数上限 */
    @Column(name = "max_members", nullable = false)
    private int maxMembers = 2;

    /** 关联群聊 ID（预留） */
    @Column(name = "chat_group_id")
    private Long chatGroupId;
}
