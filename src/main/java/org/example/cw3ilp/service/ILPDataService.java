package org.example.cw3ilp.service;

import org.example.cw3ilp.api.dto.ServicePoint;
import org.example.cw3ilp.api.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class ILPDataService {

    private static final Logger logger = LoggerFactory.getLogger(ILPDataService.class);

    private final String ilpEndpoint;
    private final RestTemplate restTemplate;

    @Autowired
    public ILPDataService(String ilpEndpoint, RestTemplate restTemplate) {
        this.ilpEndpoint = ilpEndpoint;
        this.restTemplate = restTemplate;
        logger.info("ILPDataService initialized with endpoint: {}", ilpEndpoint);
    }

    /**
     * Fetch all drones from ILP Service
     */
    public List<Drone> getAllDrones() {
        try {
            String url = ilpEndpoint + "/drones";
            logger.info("Fetching drones from: {}", url);

            ResponseEntity<Drone[]> response = restTemplate.getForEntity(url, Drone[].class);

            Drone[] drones = response.getBody();

            if (drones != null) {
                logger.info("Successfully fetched {} drones", drones.length);
                return List.of(drones);
            } else {
                logger.warn("Received null response body from drones endpoint");
                return new ArrayList<>();
            }

        } catch (Exception e) {
            logger.error("Error fetching drones from ILP service: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Fetch all service points from ILP service
     */
    public List<ServicePoint> getAllServicePoints() {
        try {
            String url = ilpEndpoint + "/service-points";
            logger.info("Fetching service points from: {}", url);

            ServicePoint[] servicePoints = restTemplate.getForObject(url, ServicePoint[].class);

            if (servicePoints != null) {
                logger.info("Successfully fetched {} service points", servicePoints.length);
                return Arrays.asList(servicePoints);
            } else {
                logger.warn("Received null response body from service-points endpoint");
                return new ArrayList<>();
            }

        } catch (Exception e) {
            logger.error("Error fetching service points");
            return new ArrayList<>();
        }
    }

    /**
     * Fetch all restricted areas from ILP service
     */
    public List<RestrictedArea> getAllRestrictedAreas() {
        try {
            String url = ilpEndpoint + "/restricted-areas";
            logger.info("Fetching restricted areas from: {}", url);

            RestrictedArea[] areas = restTemplate.getForObject(url, RestrictedArea[].class);

            if (areas != null) {
                logger.info("Successfully fetched {} restricted areas", areas.length);
                return Arrays.asList(areas);
            } else {
                return new ArrayList<>();
            }

        } catch (Exception e) {
            logger.error("Error fetching restricted areas: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }


    /**
     * Fetch availability for all drones as a map
     * Maps drone ID -> list of availability windows
     */
    public Map<String, List<Availability>> getDroneAvailabilityMap() {
        Map<String, List<Availability>> availabilityMap = new HashMap<>();
        try {
            String url = ilpEndpoint + "/drones-for-service-points";
            logger.info("Fetching drones for service points from: {}", url);

            ServicePointDrones[] allServicePointDrones = restTemplate.getForObject(
                    url,
                    ServicePointDrones[].class
            );

            if (allServicePointDrones != null) {
                logger.info("Fetched data for {} service points", allServicePointDrones.length);

                for (ServicePointDrones spDrones : allServicePointDrones) {
                    logger.info("Processing service point {}", spDrones.getServicePointId());

                    if (spDrones.getDrones() != null) {
                        logger.info("Service point {} has {} drones",
                                spDrones.getServicePointId(), spDrones.getDrones().size());

                        for (DronesAvailability da : spDrones.getDrones()) {
                            String droneId = da.getId();

                            if (da.getAvailability() != null) {
                                logger.info("Drone {} has {} availability windows",
                                        droneId, da.getAvailability().size());

                                if (!availabilityMap.containsKey(droneId)) {
                                    availabilityMap.put(droneId, new ArrayList<>(da.getAvailability()));
                                } else {
                                    availabilityMap.get(droneId).addAll(da.getAvailability());
                                }

                                logger.debug("Added availability for drone {}", droneId);
                            } else {
                                logger.warn("Drone {} has null availability", droneId);
                            }
                        }
                    } else {
                        logger.warn("Service point {} has null drones list", spDrones.getServicePointId());
                    }
                }

                logger.info("Loaded availability for {} drones total", availabilityMap.size());
                logger.info("Drone IDs with availability: {}", availabilityMap.keySet());
            } else {
                logger.warn("Received null response from drones-for-service-points");
            }

        } catch (Exception e) {
            logger.error("Error fetching drone availability: {}", e.getMessage(), e);
        }
        return availabilityMap;
    }

    /**
     * Fetch all service point + drone associations
     */
    public List<ServicePointDrones> getAllServicePointDrones() {
        try {
            String url = ilpEndpoint + "/drones-for-service-points";
            logger.info("Fetching all service point drones from: {}", url);

            ServicePointDrones[] result = restTemplate.getForObject(url, ServicePointDrones[].class);

            if (result != null) {
                logger.info("Successfully fetched {} service point drone associations", result.length);
                return Arrays.asList(result);
            } else {
                return new ArrayList<>();
            }

        } catch (Exception e) {
            logger.error("Error fetching service point drones");
            return new ArrayList<>();
        }
    }


}
