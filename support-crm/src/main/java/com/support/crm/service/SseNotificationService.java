package com.support.crm.service;


import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class SseNotificationService {

    // Jitne bhi active browsers connect honge, unki list
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    // Jab browser live stream se connect karega
    public SseEmitter subscribe(){
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));

        return emitter;
    }

    // Jab ticket mein kuch badalna ho toh sabhi connected agents ko signal bhejo
    public void broadcast(String eventType, Object data){
        for(SseEmitter emitter : emitters){
            try{
                emitter.send(SseEmitter.event().name(eventType).data(data));
            } catch (IOException e) {
                emitters.remove(emitter);
            }
        }
    }

}
