package org.example.cw3ilp.service;

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
        // Remove trailing slash if present to avoid double slashes in URLs
        this.ilpEndpoint = ilpEndpoint.endsWith("/")
            ? ilpEndpoint.substring(0, ilpEndpoint.length() - 1)
            : ilpEndpoint;
        this.restTemplate = restTemplate;
        logger.info("ILPDataService initialized with endpoint: {}", this.ilpEndpoint);
    }

    /**
     * Retry wrapper for ILP service calls with exponential backoff
     * Retries up to 3 times with delays of 500ms, 1000ms, 2000ms
     */
    private <T> T retryOnFailure(String operationName, java.util.function.Supplier<T> operation) {
        int maxRetries = 3;
        int delayMs = 500;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return operation.get();
            } catch (Exception e) {
                if (attempt == maxRetries) {
                    logger.error("Failed {} after {} attempts: {}", operationName, maxRetries, e.getMessage());
                    throw e;
                }
                logger.warn("Attempt {}/{} failed for {}: {}. Retrying in {}ms...",
                        attempt, maxRetries, operationName, e.getMessage(), delayMs);
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during retry", ie);
                }
                delayMs *= 2; // Exponential backoff
            }
        }
        throw new RuntimeException("Should not reach here");
    }

    /**
     * Fetch all drones from ILP Service with automatic retry
     */
    public List<Drone> getAllDrones() {
        return retryOnFailure("fetch drones", () -> {
            String url = ilpEndpoint + "/drones";
            logger.info("Fetching drones from: {}", url);

            ResponseEntity<Drone[]> response = restTemplate.getForEntity(url, Drone[].class);
            Drone[] drones = response.getBody();

            if (drones != null) {
                logger.info("Successfully fetched {} drones", drones.length);
                return List.of(drones);
            } else {
                logger.warn("Received null response body from drones endpoint");
                throw new RuntimeException("Null response from drones endpoint");
            }
        });
    }

    /**
     * Fetch all service points from ILP service with automatic retry
     */
    public List<DronesAvailability.ServicePoint> getAllServicePoints() {
        return retryOnFailure("fetch service points", () -> {
            String url = ilpEndpoint + "/service-points";
            logger.info("Fetching service points from: {}", url);

            DronesAvailability.ServicePoint[] servicePoints = restTemplate.getForObject(url, DronesAvailability.ServicePoint[].class);

            if (servicePoints != null) {
                logger.info("Successfully fetched {} service points", servicePoints.length);
                return Arrays.asList(servicePoints);
            } else {
                logger.warn("Received null response body from service-points endpoint");
                throw new RuntimeException("Null response from service-points endpoint");
            }
        });
    }

    /**
     * Fetch all restricted areas from ILP service with automatic retry
     */
    public List<RestrictedArea> getAllRestrictedAreas() {
        return retryOnFailure("fetch restricted areas", () -> {
            String url = ilpEndpoint + "/restricted-areas";
            logger.info("Fetching restricted areas from: {}", url);

            RestrictedArea[] areas = restTemplate.getForObject(url, RestrictedArea[].class);

            if (areas != null) {
                logger.info("Successfully fetched {} restricted areas", areas.length);
                return Arrays.asList(areas);
            } else {
                logger.warn("Received null response body from restricted-areas endpoint");
                throw new RuntimeException("Null response from restricted-areas endpoint");
            }
        });
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
