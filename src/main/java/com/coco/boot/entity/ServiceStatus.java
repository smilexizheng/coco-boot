package com.coco.boot.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ServiceStatus implements Serializable {

    private Integer ghuCount;
    private Integer ghuAliveCount;
    private Integer ghuNoAliveCount;



}
