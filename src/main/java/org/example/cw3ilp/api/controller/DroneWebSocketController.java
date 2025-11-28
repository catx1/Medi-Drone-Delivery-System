package org.example.cw3ilp.api.controller;

import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;
import org.example.cw3ilp.api.dto.CalcDeliveryPathResponse;
import org.example.cw3ilp.api.dto.DronePositionUpdate;
import org.example.cw3ilp.api.dto.MedDispatchRec;
import org.example.cw3ilp.api.model.Delivery;
import org.example.cw3ilp.api.model.DronePath;
import org.example.cw3ilp.api.model.LngLatAlt;
import org.example.cw3ilp.api.model.Drone;
import org.example.cw3ilp.service.DroneService;
import org.example.cw3ilp.service.DroneFlightSimulator;
import org.example.cw3ilp.service.DroneService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@EnableScheduling
@RequestMapping("api/v1")
public class DroneWebSocketController {

    private final DroneFlightSimulator droneFlightSimulator;
    private final SimpMessagingTemplate messagingTemplate;

    public DroneWebSocketController(DroneFlightSimulator droneFlightSimulator,
                                    SimpMessagingTemplate messagingTemplate) {
        this.droneFlightSimulator = droneFlightSimulator;
        this.messagingTemplate = messagingTemplate;
    }


    private static final Logger logger = LoggerFactory.getLogger(DroneWebSocketController.class);

    /**
     * Client sends message to /app/drone/start with flight path
     * Response broadcast to /topic/drone/position
     */
    @MessageMapping("/drone/start")
    @SendTo("/topic/drone/status")
    public String startDrone(StartFlightRequest request) {
        logger.info("Received start request for drone {}", request.getDroneId());

        try {
            droneFlightSimulator.startFlight(request.getDroneId(), request.getFlightPath());
            return "Drone " + request.getDroneId() + " started";
        } catch (Exception e) {
            logger.error("Error starting drone: {}", e.getMessage());
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Client sends message to /app/drone/stop
     */
    @MessageMapping("/drone/stop")
    @SendTo("/topic/drone/status")
    public String stopDrone() {
        logger.info("Received stop request");
        droneFlightSimulator.stopFlight();
        return "Drone stopped";
    }

    @Scheduled(fixedRate = 100)
    public void broadcastDronePosition() {
        if (droneFlightSimulator != null && droneFlightSimulator.isActive()) {
            DronePositionUpdate position = droneFlightSimulator.updatePosition();

            if (position != null) {
                logger.info("Broadcasting: droneId={}, lng={}, lat={}, status={}",
                        position.getDroneId(), position.getLng(), position.getLat(), position.getStatus());

                messagingTemplate.convertAndSend("/topic/drone/position", position);
            } else {
                logger.warn("Position update returned null!");
            }
        }
    }

    /**
     * DTO for start flight request
     */
    @Setter
    @Getter
    public static class StartFlightRequest {
        private String droneId;
        private List<LngLatAlt> flightPath;

    }

}
