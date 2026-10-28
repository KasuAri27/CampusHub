package com.campushub.task;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 信息咨询 Task 子类
 * 对应数据库 {@code t_task_consulting} 表
 */
@Entity
@Table(name = "t_task_consulting")
@DiscriminatorValue("CONSULTING")
@PrimaryKeyJoinColumn(name = "task_id")
@Getter
@Setter
@NoArgsConstructor
public class ConsultingTask extends Task {

    /**
     * 咨询方式：ONLINE / OFFLINE
     */
    @Column(name = "consulting_mode", length = 20)
    private String consultingMode = "ONLINE";

}