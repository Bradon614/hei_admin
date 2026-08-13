package com.exam.hei.endpoint.event.consumer.model;

import com.exam.hei.PojaGenerated;
import com.exam.hei.endpoint.event.model.PojaEvent;

@PojaGenerated
public record TypedEvent(String typeName, PojaEvent payload) {}
