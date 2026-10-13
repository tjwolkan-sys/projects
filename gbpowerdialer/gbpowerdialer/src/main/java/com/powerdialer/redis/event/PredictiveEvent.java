package com.powerdialer.redis.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;


@Data
@AllArgsConstructor
@SuperBuilder
public abstract class PredictiveEvent {

    private LocalDateTime createDate;
    private final String publisher;
    private final String eventClass;

    protected PredictiveEvent(String publisher) {
        this.publisher = publisher;
        this.eventClass = this.getClass().getSimpleName();
        this.createDate = LocalDateTime.now();
    }

}
