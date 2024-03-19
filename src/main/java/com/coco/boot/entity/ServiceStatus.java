package com.coco.boot.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ServiceStatus implements Serializable {

    private Long ghuCount;
    private Integer aliveCount;
    private Integer noAliveCount;
    private Long noAliveTotal;



}
