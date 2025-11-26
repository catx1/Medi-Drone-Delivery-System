package org.example.cw3ilp.service;

import org.example.cw3ilp.api.dto.*;
import org.example.cw3ilp.api.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.example.cw3ilp.api.dto.ServicePoint;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DroneService {

    private static final Logger logger = LoggerFactory.getLogger(DroneService.class);
    private final ILPDataService ilpDataService;

    @Autowired
    public DroneService(ILPDataService ilpDataService) {
        this.ilpDataService = ilpDataService;
    }

    public List<String> getDronesWithCooling(boolean hasCooling) {
        List<Drone> allDrones = ilpDataService.getAllDrones();

        logger.info("Fetched {} drones", allDrones.size());

        return allDrones.stream()
                .filter(drone -> {
                    if (drone.getCapability() == null) {
                        return false;
                    }
                    Boolean cooling = drone.getCapability().getCooling();
                    boolean actualCooling = (cooling != null && cooling);
                    return actualCooling == hasCooling;
                })
                .map(Drone::getId)
                .collect(Collectors.toList());
    }


    public Optional<Drone> getDroneById(String id) {
        List<Drone> allDrones = ilpDataService.getAllDrones();

        return allDrones.stream()
                .filter(drone -> drone.getId().equals(id))
                .findFirst();
    }


    public List<String> queryDronesByAttribute(String attributeName, String attributeValue) {
        List<Drone> allDrones = ilpDataService.getAllDrones();
        List<String> matchingIds = new ArrayList<>();

        // convert to json and query
        for (Drone drone : allDrones) {
            Object actualValue = null;

            if (attributeName.equals("name") || attributeName.equals("id")) {
                actualValue = getTopLevelAttribute(drone, attributeName);
            } else {
                if (drone.getCapability() != null) {
                    actualValue = getCapabilityAttribute(drone.getCapability(), attributeName);
                }
            }
                if (actualValue != null && actualValue.toString().equals(attributeValue)) {
                    matchingIds.add(drone.getId());
                }

        }

        return matchingIds;
    }

    // Helpers
    private Object getTopLevelAttribute(Drone drone, String attributeName) {
        return switch (attributeName) {
            case "id" -> drone.getId();
            case "name" -> drone.getName();
            default -> null;
        };
    }

    private Object getCapabilityAttribute(Capability capability, String attributeName) {
        return switch (attributeName) {
            case "cooling" -> capability.getCooling();
            case "heating" -> capability.getHeating();
            case "capacity" -> capability.getCapacity();
            case "maxMoves" -> capability.getMaxMoves();
            case "costPerMove" -> capability.getCostPerMove();
            case "costInitial" -> capability.getCostInitial();
            case "costFinal" -> capability.getCostFinal();
            default -> null;
        };
    }

    public List<String> queryDronesByCriteria(List<QueryCriteriaRequest> criteria) {
        List<Drone> allDrones = ilpDataService.getAllDrones();
        List<String> matchingIds = new ArrayList<>();

        for (Drone drone : allDrones) {
            boolean matchesAll = true;

            for (QueryCriteriaRequest criterion : criteria) {
                Object actualValue = getAttributeValue(drone, criterion.getAttribute());

                if (!matchesCriterion(actualValue, criterion.getOperator(), criterion.getValue())) {
                    matchesAll = false;
                    break;
                }
            }

            if (matchesAll) {
                matchingIds.add(drone.getId());
            }
        }

        return matchingIds;
    }

    private boolean matchesCriterion(Object actualValue, String operator, String expectedValue) {

        if (actualValue == null) {
            return false;
        }

        String actualStr = actualValue.toString();

        return switch (actualValue) {
            case Number number -> compareNumeric(actualStr, operator, expectedValue);
            case String s -> compareString(actualStr, operator, expectedValue);
            case Boolean b -> compareBoolean(actualStr, operator, expectedValue);
            default -> false;
        };

    }

    private Object getAttributeValue(Drone drone, String attributeName) {
        if (attributeName.equals("name") || attributeName.equals("id")) {
            return getTopLevelAttribute(drone, attributeName);
        } else if (drone.getCapability() != null) {
            return getCapabilityAttribute(drone.getCapability(), attributeName);
        }
        return null;
    }

    private boolean compareNumeric(String actualStr, String operator, String expectedValue) {
        try {
            double actualDouble = Double.parseDouble(actualStr);
            double expectedDouble = Double.parseDouble(expectedValue);

            return switch (operator) {
                case "=" -> actualDouble == expectedDouble;
                case "!=" -> actualDouble != expectedDouble;
                case ">" -> actualDouble > expectedDouble;
                case "<" -> actualDouble < expectedDouble;
                case ">=" -> actualDouble >= expectedDouble;
                case "<=" -> actualDouble <= expectedDouble;
                default -> false;
            };
        } catch(NumberFormatException e){
                return false;
            }
        }

    private boolean compareString(String actualStr, String operator, String expectedStr) {
        return switch (operator) {
            case "=" -> actualStr.equalsIgnoreCase(expectedStr);
            case "!=" -> !actualStr.equalsIgnoreCase(expectedStr);
            default -> false;
        };
    }

    private boolean compareBoolean(String actualStr, String operator, String expectedStr) {
        return switch (operator) {
            case "=" -> actualStr.equalsIgnoreCase(expectedStr);
            case "!=" -> !actualStr.equalsIgnoreCase(expectedStr);
            default -> false;
        };
    }

    // drone availability map from ilp service
    private Map<String, List<Availability>> getDroneAvailabilityMap() {
        return ilpDataService.getDroneAvailabilityMap();
    }

    public List<String> findAvailableDrones(List<MedDispatchRec> medDispatchRecs) {
        List<Drone> allDrones = ilpDataService.getAllDrones();
        Map<String, List<Availability>> droneAvailability = getDroneAvailabilityMap();
        List<String> availableDroneIds = new ArrayList<>();
        logger.info("Number of dispatches to fulfill: {}", medDispatchRecs.size());

        for (int i = 0; i < medDispatchRecs.size(); i++) {
            MedDispatchRec dispatch = medDispatchRecs.get(i);
            Requirements req = dispatch.getRequirements();
            logger.info("Dispatch #{} (ID={}): capacity={}, cooling={}, heating={}, maxCost={}",
                    i + 1,
                    dispatch.getId(),
                    req.getCapacity(),
                    req.getCooling(),
                    req.getHeating(),
                    req.getMaxCost());
        }

        for (Drone drone : allDrones) {
            logger.info("--- Checking Drone {} ---", drone.getId());
            if (canDroneHandleAllDispatches(drone, medDispatchRecs, droneAvailability)) {
                availableDroneIds.add(drone.getId());
            }
        }

        logger.info("Available drones: {} out of {}", availableDroneIds.size(), allDrones.size());
        logger.info("Drone IDs: {}", availableDroneIds);
    return availableDroneIds;
    }

    private boolean canDroneHandleAllDispatches(
            Drone drone,
            List<MedDispatchRec> medDispatchRecs,
            Map<String, List<Availability>> droneAvailability
    ) {
        for (MedDispatchRec medDispatchRec : medDispatchRecs) {
            if (!canDroneHandleDispatch(drone, medDispatchRec, droneAvailability)) {
                return false;
            }
        }
        return true;
    }

    private boolean canDroneHandleDispatch(
            Drone drone,
            MedDispatchRec medDispatchRec,
            Map<String, List<Availability>> droneAvailability
    ) {
        Capability capability = drone.getCapability();
        Requirements requirements = medDispatchRec.getRequirements();


        if (!checkCapacity(capability, requirements)) {
            return false;
        }

        if (!checkCooling(capability, requirements)) {
            return false;
        }

        if (!checkHeating(capability, requirements)) {
            return false;
        }

        if (medDispatchRec.getDate() != null && medDispatchRec.getTime() != null) {
            if (!checkAvailability(drone, medDispatchRec, droneAvailability)) {
                return false;
            }
        }

        if (requirements.getMaxCost() != null) {
            return checkCost(capability, requirements);
        }
        // passed all checks
        return true;
    }

    private boolean checkCapacity(Capability capability, Requirements requirements) {
        if (requirements.getCapacity() == null) {
            return true;
        }

        Double droneCapacity = capability.getCapacity();
        Double requirementsCapacity = requirements.getCapacity();

        if (droneCapacity == null) {
            return false;
        }

        return droneCapacity >= requirementsCapacity;
    }

    private boolean checkCooling(Capability capability, Requirements requirements) {
        Boolean requiresCooling = requirements.getCooling();
        if (requiresCooling == null || Boolean.FALSE.equals(requiresCooling)) {
            return true;
        }

        Boolean hasCooling = capability.getCooling();

        return Boolean.TRUE.equals(hasCooling);
    }

    private boolean checkHeating(Capability capability, Requirements requirements) {
        Boolean requiresHeating = requirements.getHeating();

        if (requiresHeating == null || Boolean.FALSE.equals(requiresHeating)) {
            return true;
        }

        Boolean hasHeating = capability.getHeating();

        return Boolean.TRUE.equals(hasHeating);
    }

    private boolean checkAvailability(
            Drone drone,
            MedDispatchRec medDispatchRec,
            Map<String, List<Availability>> droneAvailability
    ) {
        try {
            java.time.LocalDate dispatchDate = java.time.LocalDate.parse(medDispatchRec.getDate());
            java.time.LocalTime dispatchTime = java.time.LocalTime.parse(medDispatchRec.getTime());
            java.time.DayOfWeek javaDayOfWeek = dispatchDate.getDayOfWeek();

            logger.debug("Checking availability for drone {} on {} at {}",
                    drone.getId(), javaDayOfWeek, dispatchTime);

            List<Availability> availabilityList = droneAvailability.get(drone.getId());

            if (availabilityList == null || availabilityList.isEmpty()) {
                logger.debug("No availability info for drone {}", drone.getId());
                return false;
            }

            for (Availability availability : availabilityList) {
                DayOfWeek apiDayOfWeek = availability.getDayOfWeek();

                if (apiDayOfWeek != null && apiDayOfWeek.name().equals(javaDayOfWeek.name())) {
                    java.time.LocalTime from = availability.getFrom();
                    java.time.LocalTime until = availability.getUntil();

                    if (from != null && until != null) {
                        logger.debug("Checking time window: {} to {}", from, until);

                        if (isTimeInRange(dispatchTime, from, until)) {
                            logger.debug("Drone {} IS available", drone.getId());
                            return true;
                        }
                    }
                }
            }

            logger.debug("Drone {} NOT available on {} at {}",
                    drone.getId(), javaDayOfWeek, dispatchTime);
            return false;

        } catch (Exception e) {
            logger.error("Error checking availability for drone {}: {}",
                    drone.getId(), e.getMessage(), e);
            return false;
        }
    }

    private boolean isTimeInRange(java.time.LocalTime time, java.time.LocalTime from, java.time.LocalTime until) {
        return !time.isBefore(from) && !time.isAfter(until);
    }

    private boolean checkCost(Capability capability, Requirements requirements) {
        Double maxCost = requirements.getMaxCost();

        if (maxCost == null) {
            logger.debug("    No cost constraint");
            return true;
        }

        Double costInitial = capability.getCostInitial();
        Double costFinal = capability.getCostFinal();
        Double costPerMove = capability.getCostPerMove();

        if (costInitial == null || costFinal == null || costPerMove == null) {
            return false;
        }

        double estimatedCost = costInitial + costFinal + (100 * costPerMove);

        return estimatedCost <= maxCost;
    }


    @Autowired
    private PathfinderService pathfinderService;


    /**
     * Calculate optimal delivery paths for a list of dispatch requests
     */
    public CalcDeliveryPathResponse calculateDeliveryPath(List<MedDispatchRec> dispatches) {


        logger.info("Calculating delivery path for {} dispatches", dispatches.size());

        List<Drone> allDrones = ilpDataService.getAllDrones();
        List<ServicePoint> servicePoints = ilpDataService.getAllServicePoints();
        List<RestrictedArea> restrictedAreas = ilpDataService.getAllRestrictedAreas();
        List<ServicePointDrones> droneAssociations = ilpDataService.getAllServicePointDrones();
        Map<String, List<Availability>> availabilityMap = ilpDataService.getDroneAvailabilityMap();

        logger.info("Loaded {} drones, {} service points, {} restricted areas",
                allDrones.size(), servicePoints.size(), restrictedAreas.size());

        Map<String, Drone> droneMap = buildDroneMap(allDrones);
        Map<Integer, ServicePoint> servicePointMap = buildServicePointMap(servicePoints);

        List<DronePath> dronePaths = new ArrayList<>();
        int totalMoves = 0;
        double totalCost = 0.0;

        List<PlannedRoute> routes = planRoutes(
                dispatches,
                servicePoints,
                droneAssociations,
                droneMap,
                availabilityMap,
                restrictedAreas
        );

        for (PlannedRoute route : routes) {
            DronePath dronePath = generateDronePath(
                    route,
                    servicePointMap,
                    restrictedAreas
            );

            if (dronePath != null && !dronePath.getDeliveries().isEmpty()) {
                dronePaths.add(dronePath);

                int routeMoves = countMoves(dronePath);
                double routeCost = calculateRouteCost(routeMoves, route.getDrone());

                totalMoves += routeMoves;
                totalCost += routeCost;
            }
        }

        CalcDeliveryPathResponse response = new CalcDeliveryPathResponse();
        response.setTotalMoves(totalMoves);
        response.setTotalCost(totalCost);
        response.setDronePaths(dronePaths);

        logger.info("Calculated delivery path: {} moves, {} cost, {} drone paths",
                totalMoves, totalCost, dronePaths.size());

        for (ServicePoint sp : servicePoints) {
            if (sp.getLocation() != null) {
                logger.info("  {} (id={}): lng={}, lat={}",
                        sp.getName(), sp.getId(),
                        sp.getLocation().getLng(), sp.getLocation().getLat());
            } else {
                logger.warn("  {} (id={}): location is null!", sp.getName(), sp.getId());
            }
        }

        return response;
    }


    /**
     * Internal class to represent a planned route
     */
    private static class PlannedRoute {
        private Drone drone;
        private ServicePoint servicePoint;
        private List<MedDispatchRec> dispatches;

        public PlannedRoute(Drone drone, ServicePoint servicePoint) {
            this.drone = drone;
            this.servicePoint = servicePoint;
            this.dispatches = new ArrayList<>();
        }

        public Drone getDrone() { return drone; }
        public ServicePoint getServicePoint() { return servicePoint; }
        public List<MedDispatchRec> getDispatches() { return dispatches; }

        public void addDispatch(MedDispatchRec dispatch) {
            dispatches.add(dispatch);
        }
    }


    private Map<String, Drone> buildDroneMap(List<Drone> drones) {
        Map<String, Drone> map = new HashMap<>();
        for (Drone drone : drones) {
            map.put(drone.getId(), drone);
        }
        return map;
    }

    private Map<Integer, ServicePoint> buildServicePointMap(List<ServicePoint> servicePoints) {
        Map<Integer, ServicePoint> map = new HashMap<>();
        for (ServicePoint sp : servicePoints) {
            map.put(sp.getId(), sp);
        }
        return map;
    }


    /**
     * Plan routes - assign dispatches to drones at the CLOSEST service points
     * Allows multiple drones from different service points
     */
    private List<PlannedRoute> planRoutes(
            List<MedDispatchRec> dispatches,
            List<ServicePoint> servicePoints,
            List<ServicePointDrones> droneAssociations,
            Map<String, Drone> droneMap,
            Map<String, List<Availability>> availabilityMap,
            List<RestrictedArea> restrictedAreas
    ) {
        List<PlannedRoute> routes = new ArrayList<>();
        List<MedDispatchRec> unassigned = new ArrayList<>(dispatches);

        Set<String> usedDrones = new HashSet<>();

        while (!unassigned.isEmpty()) {
            MedDispatchRec dispatch = unassigned.get(0);

            ServicePoint closestSP = findClosestServicePoint(dispatch.getDelivery(), servicePoints);

            if (closestSP == null) {
                logger.warn("No service point found for dispatch {}", dispatch.getId());
                unassigned.remove(0);
                continue;
            }

            logger.info("Closest service point for dispatch {} is {} (id={})",
                    dispatch.getId(), closestSP.getName(), closestSP.getId());

            ServicePointDrones spDrones = findDroneAssociationForServicePoint(
                    closestSP.getId(), droneAssociations);

            if (spDrones == null) {
                logger.warn("No drone association for service point {}", closestSP.getId());
                unassigned.remove(0);
                continue;
            }

            boolean assigned = false;
            for (DronesAvailability da : spDrones.getDrones()) {
                String droneId = da.getId();
                if (usedDrones.contains(droneId)) continue;

                Drone drone = droneMap.get(droneId);
                if (drone == null) continue;

                List<Availability> availability = availabilityMap.get(droneId);

                if (!canDroneHandleDispatch(drone, dispatch, availability)) {
                    continue;
                }

                PlannedRoute route = new PlannedRoute(drone, closestSP);
                int estimatedMoves = 0;
                int maxMoves = drone.getCapability().getMaxMoves();

                int dispatchMoves = estimateMovesForDispatch(
                        closestSP.getLocation(),
                        dispatch.getDelivery(),
                        restrictedAreas
                );

                if (dispatchMoves * 2 < maxMoves) {
                    route.addDispatch(dispatch);
                    estimatedMoves += dispatchMoves;
                    unassigned.remove(dispatch);
                    assigned = true;

                    logDroneSelection(drone, closestSP, dispatchMoves, route.getDispatches().size());

                    Iterator<MedDispatchRec> iterator = unassigned.iterator();
                    while (iterator.hasNext()) {
                        MedDispatchRec otherDispatch = iterator.next();

                        ServicePoint otherClosestSP = findClosestServicePoint(
                                otherDispatch.getDelivery(), servicePoints);

                        if (otherClosestSP == null || !otherClosestSP.getId().equals(closestSP.getId())) {
                            logger.info("Dispatch {} is closer to {} - will use different drone",
                                    otherDispatch.getId(),
                                    otherClosestSP != null ? otherClosestSP.getName() : "unknown");
                            continue;
                        }

                        if (!canDroneHandleDispatch(drone, otherDispatch, availability)) {
                            continue;
                        }

                        int otherDispatchMoves = estimateMovesForDispatch(
                                closestSP.getLocation(),
                                otherDispatch.getDelivery(),
                                restrictedAreas
                        );

                        if (estimatedMoves + otherDispatchMoves * 2 < maxMoves) {
                            route.addDispatch(otherDispatch);
                            estimatedMoves += otherDispatchMoves;
                            iterator.remove();
                            logger.info("Added dispatch {} to same drone route", otherDispatch.getId());
                        }
                    }

                    routes.add(route);
                    usedDrones.add(droneId);
                    break;
                }
            }

            // if no drone could handle it then try other service points
            if (!assigned) {
                logger.warn("No available drone at {} for dispatch {}, trying other service points...",
                        closestSP.getName(), dispatch.getId());

                for (ServicePoint sp : servicePoints) {
                    if (sp.getId().equals(closestSP.getId())) continue;

                    ServicePointDrones otherSpDrones = findDroneAssociationForServicePoint(
                            sp.getId(), droneAssociations);

                    if (otherSpDrones == null) continue;

                    for (DronesAvailability da : otherSpDrones.getDrones()) {
                        String droneId = da.getId();
                        if (usedDrones.contains(droneId)) continue;

                        Drone drone = droneMap.get(droneId);
                        if (drone == null) continue;

                        List<Availability> availability = availabilityMap.get(droneId);

                        if (!canDroneHandleDispatch(drone, dispatch, availability)) continue;

                        int maxMoves = drone.getCapability().getMaxMoves();
                        int dispatchMoves = estimateMovesForDispatch(
                                sp.getLocation(), dispatch.getDelivery(), restrictedAreas);

                        if (dispatchMoves * 2 < maxMoves) {
                            PlannedRoute route = new PlannedRoute(drone, sp);
                            route.addDispatch(dispatch);
                            routes.add(route);
                            usedDrones.add(droneId);
                            unassigned.remove(dispatch);
                            assigned = true;

                            logger.info("Assigned dispatch {} to drone {} at backup service point {}",
                                    dispatch.getId(), droneId, sp.getName());
                            logDroneSelection(drone, sp, dispatchMoves, 1);
                            break;
                        }
                    }
                    if (assigned) break;
                }

                if (!assigned) {
                    logger.warn("Could not assign dispatch {} to any drone", dispatch.getId());
                    unassigned.remove(dispatch);
                }
            }
        }

        logger.info("Created {} routes using {} drones", routes.size(), usedDrones.size());
        return routes;
    }

    /**
     * Find the closest service point to a delivery location
     */
    private ServicePoint findClosestServicePoint(LngLatAlt deliveryLocation, List<ServicePoint> servicePoints) {
        if (deliveryLocation == null || servicePoints == null || servicePoints.isEmpty()) {
            return null;
        }

        ServicePoint closest = null;
        double minDistance = Double.MAX_VALUE;

        logger.info("Finding closest service point for delivery at ({}, {})",
                deliveryLocation.getLng(), deliveryLocation.getLat());

        for (ServicePoint sp : servicePoints) {
            if (sp.getLocation() == null) {
                logger.warn("  Service point {} has null location!", sp.getName());
                continue;
            }

            double distance = pathfinderService.calculateDistance(sp.getLocation(), deliveryLocation);

            logger.info("  Distance to {} (id={}): {} (SP at {}, {})",
                    sp.getName(), sp.getId(), distance,
                    sp.getLocation().getLng(), sp.getLocation().getLat());

            if (distance < minDistance) {
                minDistance = distance;
                closest = sp;
            }
        }

        if (closest != null) {
            logger.info("  -> CLOSEST: {} with distance {}", closest.getName(), minDistance);
        }

        return closest;
    }

    /**
     * Find drone association for a specific service point
     */
    private ServicePointDrones findDroneAssociationForServicePoint(
            Integer servicePointId,
            List<ServicePointDrones> droneAssociations
    ) {
        for (ServicePointDrones spd : droneAssociations) {
            if (spd.getServicePointId().equals(servicePointId)) {
                return spd;
            }
        }
        return null;
    }


    /**
     * Estimate moves needed for a dispatch (straight-line estimate)
     */
    private int estimateMovesForDispatch(LngLatAlt from, LngLatAlt to, List<RestrictedArea> zones) {
        double distance = pathfinderService.calculateDistance(from, to);
        return (int) Math.ceil(distance / 0.00015 * 1.5);
    }

    private boolean canDroneHandleDispatch(
            Drone drone,
            MedDispatchRec dispatch,
            List<Availability> availability
    ) {
        Requirements req = dispatch.getRequirements();
        Capability cap = drone.getCapability();

        if (req.getCapacity() != null && cap.getCapacity() < req.getCapacity()) {
            logger.debug("Drone {} fails capacity check: has {}, needs {}",
                    drone.getId(), cap.getCapacity(), req.getCapacity());
            return false;
        }

        if (Boolean.TRUE.equals(req.getCooling()) && !Boolean.TRUE.equals(cap.getCooling())) {
            logger.debug("Drone {} fails cooling check", drone.getId());
            return false;
        }

        if (Boolean.TRUE.equals(req.getHeating()) && !Boolean.TRUE.equals(cap.getHeating())) {
            logger.debug("Drone {} fails heating check", drone.getId());
            return false;
        }

        if (!isAvailableAtTime(dispatch, availability)) {
            logger.info("Drone {} NOT available at {} {}",
                    drone.getId(), dispatch.getDate(), dispatch.getTime());
            return false;
        }

        return true;
    }

    private boolean isAvailableAtTime(MedDispatchRec dispatch, List<Availability> availability) {
        if (availability == null || availability.isEmpty()) {
            return false;
        }

        try {
            LocalDate date = LocalDate.parse(dispatch.getDate());
            LocalTime time = LocalTime.parse(dispatch.getTime());
            java.time.DayOfWeek dayOfWeek = date.getDayOfWeek();

            for (Availability a : availability) {
                if (a.getDayOfWeek().name().equals(dayOfWeek.name())) {
                    if (!time.isBefore(a.getFrom()) && !time.isAfter(a.getUntil())) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Error parsing date/time: {}", e.getMessage());
        }

        return false;
    }

    /**
     * Generate a DronePath with flight paths for each delivery
     */
    private DronePath generateDronePath(
            PlannedRoute route,
            Map<Integer, ServicePoint> servicePointMap,
            List<RestrictedArea> restrictedAreas
    ) {
        ServicePoint sp = route.getServicePoint();
        List<MedDispatchRec> dispatches = route.getDispatches();

        if (dispatches.isEmpty()) {
            return null;
        }

        for (MedDispatchRec dispatch : dispatches) {
            logger.info("Dispatch {}: delivery={}", dispatch.getId(), dispatch.getDelivery());
            if (dispatch.getDelivery() != null) {
                logger.info("  -> lng={}, lat={}",
                        dispatch.getDelivery().getLng(),
                        dispatch.getDelivery().getLat());
            }
        }

        List<MedDispatchRec> sequence = optimizeSequence(sp.getLocation(), dispatches, restrictedAreas);

        PathBuildResult result = buildCompleteFlightPathWithTracking(
                sp.getLocation(),
                sequence,
                restrictedAreas
        );

        if (result == null || result.path.isEmpty()) {
            logger.warn("Could not build flight path for route");
            return null;
        }

        List<Delivery> deliveries = splitPathIntoDeliveries(result.path, result.completedDeliveries);

        DronePath dronePath = new DronePath();
        dronePath.setDroneId(route.getDrone().getId());
        dronePath.setDeliveries(deliveries);

        return dronePath;
    }


    /**
     * Result of building a flight path
     */
    private static class PathBuildResult {
        List<LngLatAlt> path;
        List<MedDispatchRec> completedDeliveries;

        PathBuildResult(List<LngLatAlt> path, List<MedDispatchRec> completedDeliveries) {
            this.path = path;
            this.completedDeliveries = completedDeliveries;
        }
    }

    /**
     * Build complete flight path and track which deliveries were completed
     */
    private PathBuildResult buildCompleteFlightPathWithTracking(
            LngLatAlt servicePointLocation,
            List<MedDispatchRec> sequence,
            List<RestrictedArea> restrictedAreas
    ) {
        List<LngLatAlt> completePath = new ArrayList<>();
        List<MedDispatchRec> completedDeliveries = new ArrayList<>();
        LngLatAlt current = servicePointLocation;

        for (MedDispatchRec dispatch : sequence) {
            LngLatAlt deliveryPoint = dispatch.getDelivery();

            if (deliveryPoint == null || deliveryPoint.getLng() == null || deliveryPoint.getLat() == null) {
                logger.warn("Skipping dispatch {} - invalid delivery point", dispatch.getId());
                continue;
            }

            List<LngLatAlt> segment = pathfinderService.findPath(current, deliveryPoint, restrictedAreas);

            if (segment == null || segment.isEmpty()) {
                logger.warn("Skipping dispatch {} - no path found (possibly in restricted area)", dispatch.getId());
                continue;
            }

            if (completePath.isEmpty()) {
                completePath.addAll(segment);
            } else {
                completePath.addAll(segment.subList(1, segment.size()));
            }

            // add hover
            LngLatAlt lastPos = completePath.get(completePath.size() - 1);
            completePath.add(new LngLatAlt(lastPos.getLng(), lastPos.getLat(), lastPos.getAlt()));

            current = lastPos;
            completedDeliveries.add(dispatch);

            logger.info("Successfully added delivery {} to path", dispatch.getId());
        }

        if (completedDeliveries.isEmpty()) {
            logger.warn("No deliveries could be completed");
            return null;
        }

        List<LngLatAlt> returnPath = pathfinderService.findPath(current, servicePointLocation, restrictedAreas);
        if (returnPath == null || returnPath.isEmpty()) {
            logger.warn("No path found to return to service point");
            return null;
        }
        completePath.addAll(returnPath.subList(1, returnPath.size()));

        logger.info("Completed {} out of {} deliveries", completedDeliveries.size(), sequence.size());
        return new PathBuildResult(completePath, completedDeliveries);
    }

    /**
     * Optimize delivery sequence using greedy nearest neighbor
     */
    private List<MedDispatchRec> optimizeSequence(
            LngLatAlt start,
            List<MedDispatchRec> dispatches,
            List<RestrictedArea> restrictedAreas
    ) {
        List<MedDispatchRec> sequence = new ArrayList<>();
        List<MedDispatchRec> remaining = new ArrayList<>(dispatches);
        LngLatAlt current = start;

        while (!remaining.isEmpty()) {
            MedDispatchRec nearest = null;
            double nearestDistance = Double.MAX_VALUE;

            for (MedDispatchRec dispatch : remaining) {
                double distance = pathfinderService.calculateDistance(current, dispatch.getDelivery());
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = dispatch;
                }
            }

            if (nearest != null) {
                sequence.add(nearest);
                remaining.remove(nearest);
                current = nearest.getDelivery();
            }
        }

        return sequence;
    }

    /**
     * Split complete path into individual Delivery objects
     */
    private List<Delivery> splitPathIntoDeliveries(
            List<LngLatAlt> completePath,
            List<MedDispatchRec> sequence
    ) {
        List<Delivery> deliveries = new ArrayList<>();
        int pathIndex = 0;

        for (MedDispatchRec dispatch : sequence) {
            List<LngLatAlt> deliveryPath = new ArrayList<>();

            while (pathIndex < completePath.size()) {
                LngLatAlt current = completePath.get(pathIndex);
                deliveryPath.add(current);
                pathIndex++;

                if (pathIndex < completePath.size()) {
                    LngLatAlt next = completePath.get(pathIndex);
                    if (isHover(current, next)) {
                        deliveryPath.add(next);
                        pathIndex++;
                        break;
                    }
                }
            }

            Delivery delivery = new Delivery();
            delivery.setDeliveryId(dispatch.getId());
            delivery.setFlightPath(deliveryPath);
            deliveries.add(delivery);
        }

        // Add return journey as separate delivery with null ID
        if (pathIndex < completePath.size()) {
            List<LngLatAlt> returnPath = new ArrayList<>();
            while (pathIndex < completePath.size()) {
                returnPath.add(completePath.get(pathIndex));
                pathIndex++;
            }

            if (!returnPath.isEmpty()) {
                Delivery returnDelivery = new Delivery();
                returnDelivery.setDeliveryId(null);
                returnDelivery.setFlightPath(returnPath);
                deliveries.add(returnDelivery);
            }
        }

        return deliveries;
    }
    private boolean isHover(LngLatAlt p1, LngLatAlt p2) {
        double tolerance = 0.0000001;
        return Math.abs(p1.getLng() - p2.getLng()) < tolerance &&
                Math.abs(p1.getLat() - p2.getLat()) < tolerance;
    }


    private int countMoves(DronePath dronePath) {
        int moves = 0;
        for (Delivery delivery : dronePath.getDeliveries()) {
            moves += delivery.getFlightPath().size() - 1;
        }
        return moves;
    }

    private double calculateRouteCost(int moves, Drone drone) {
        Capability cap = drone.getCapability();
        return cap.getCostInitial()
                + (moves * cap.getCostPerMove())
                + cap.getCostFinal();
    }

    /**
     * Remove consecutive duplicate coordinates
     */
    private List<List<Double>> removeConsecutiveDuplicates(List<List<Double>> coordinates) {
        if (coordinates == null || coordinates.size() < 2) {
            return coordinates;
        }

        List<List<Double>> cleaned = new ArrayList<>();
        cleaned.add(coordinates.get(0));

        for (int i = 1; i < coordinates.size(); i++) {
            List<Double> current = coordinates.get(i);
            List<Double> previous = cleaned.get(cleaned.size() - 1);

            if (!current.get(0).equals(previous.get(0)) ||
                    !current.get(1).equals(previous.get(1))) {
                cleaned.add(current);
            }
        }

        return cleaned;
    }

    /**
     * Build GeoJSON FeatureCollection with LineString geometry
     */
    private Map<String, Object> buildGeoJsonFeatureCollection(List<List<Double>> coordinates) {
        Map<String, Object> geometry = new LinkedHashMap<>();
        geometry.put("type", "LineString");
        geometry.put("coordinates", coordinates);

        Map<String, Object> feature = new LinkedHashMap<>();
        feature.put("type", "Feature");
        feature.put("properties", new LinkedHashMap<>());
        feature.put("geometry", geometry);

        Map<String, Object> featureCollection = new LinkedHashMap<>();
        featureCollection.put("type", "FeatureCollection");

        List<Map<String, Object>> features = new ArrayList<>();
        features.add(feature);
        featureCollection.put("features", features);

        return featureCollection;
    }

    /**
     * Create empty FeatureCollection for error cases
     */
    private Map<String, Object> createEmptyFeatureCollection() {
        Map<String, Object> empty = new LinkedHashMap<>();
        empty.put("type", "FeatureCollection");
        empty.put("features", new ArrayList<>());
        return empty;
    }

    /**
     * Calculate delivery path for a single drone
     * Used by calcDeliveryPathAsGeoJson endpoint
     */
    public Map<String, Object> calculateSingleDronePathAsGeoJson(List<MedDispatchRec> dispatches) {
        logger.info("Calculating single-drone delivery path as GeoJSON for {} dispatches", dispatches.size());

        List<Drone> allDrones = ilpDataService.getAllDrones();
        List<ServicePoint> servicePoints = ilpDataService.getAllServicePoints();
        List<RestrictedArea> restrictedAreas = ilpDataService.getAllRestrictedAreas();
        List<ServicePointDrones> droneAssociations = ilpDataService.getAllServicePointDrones();
        Map<String, List<Availability>> availabilityMap = ilpDataService.getDroneAvailabilityMap();

        ServicePoint bestServicePoint = findBestServicePointForDeliveries(dispatches, servicePoints);

        if (bestServicePoint == null) {
            logger.warn("No suitable service point found");
            return createEmptyFeatureCollection();
        }

        logger.info("Selected service point: {} (id={})", bestServicePoint.getName(), bestServicePoint.getId());

        Drone selectedDrone = findDroneForAllDispatches(
                dispatches,
                bestServicePoint,
                droneAssociations,
                allDrones,
                availabilityMap
        );

        if (selectedDrone == null) {
            logger.warn("No single drone can handle all dispatches");
            return createEmptyFeatureCollection();
        }

        logDroneSelection(selectedDrone, bestServicePoint, 0, dispatches.size());

        List<MedDispatchRec> optimizedSequence = optimizeSequence(
                bestServicePoint.getLocation(),
                dispatches,
                restrictedAreas
        );

        PathBuildResult pathResult = buildCompleteFlightPathWithTracking(
                bestServicePoint.getLocation(),
                optimizedSequence,
                restrictedAreas
        );

        if (pathResult == null || pathResult.path.isEmpty()) {
            logger.warn("Could not build flight path");
            return createEmptyFeatureCollection();
        }

        List<List<Double>> coordinates = new ArrayList<>();
        for (LngLatAlt point : pathResult.path) {
            if (point.getLng() != null && point.getLat() != null) {
                List<Double> coord = new ArrayList<>();
                coord.add(point.getLng());
                coord.add(point.getLat());
                coordinates.add(coord);
            }
        }

        List<List<Double>> cleanedCoordinates = removeConsecutiveDuplicates(coordinates);

        if (cleanedCoordinates.size() < 2) {
            logger.warn("Not enough coordinates for LineString");
            return createEmptyFeatureCollection();
        }

        logger.info("Generated GeoJSON with {} coordinates", cleanedCoordinates.size());
        return buildGeoJsonFeatureCollection(cleanedCoordinates);
    }

    /**
     * Find the best service point based on average distance to all deliveries
     */
    private ServicePoint findBestServicePointForDeliveries(
            List<MedDispatchRec> dispatches,
            List<ServicePoint> servicePoints
    ) {
        if (servicePoints == null || servicePoints.isEmpty()) {
            return null;
        }

        ServicePoint best = null;
        double bestTotalDistance = Double.MAX_VALUE;

        for (ServicePoint sp : servicePoints) {
            if (sp.getLocation() == null) continue;

            double totalDistance = 0;
            for (MedDispatchRec dispatch : dispatches) {
                if (dispatch.getDelivery() != null) {
                    totalDistance += pathfinderService.calculateDistance(
                            sp.getLocation(),
                            dispatch.getDelivery()
                    );
                }
            }

            if (totalDistance < bestTotalDistance) {
                bestTotalDistance = totalDistance;
                best = sp;
            }
        }

        return best;
    }

    /**
     * Find a single drone that can handle ALL dispatches
     */
    private Drone findDroneForAllDispatches(
            List<MedDispatchRec> dispatches,
            ServicePoint servicePoint,
            List<ServicePointDrones> droneAssociations,
            List<Drone> allDrones,
            Map<String, List<Availability>> availabilityMap
    ) {
        ServicePointDrones spDrones = null;
        for (ServicePointDrones spd : droneAssociations) {
            if (spd.getServicePointId().equals(servicePoint.getId())) {
                spDrones = spd;
                break;
            }
        }

        if (spDrones == null || spDrones.getDrones() == null) {
            return null;
        }

        Map<String, Drone> droneMap = new HashMap<>();
        for (Drone d : allDrones) {
            droneMap.put(d.getId(), d);
        }

        for (DronesAvailability da : spDrones.getDrones()) {
            Drone drone = droneMap.get(da.getId());
            if (drone == null) continue;

            List<Availability> availability = availabilityMap.get(da.getId());

            boolean canHandleAll = true;
            for (MedDispatchRec dispatch : dispatches) {
                if (!canDroneHandleDispatch(drone, dispatch, availability)) {
                    canHandleAll = false;
                    break;
                }
            }

            if (canHandleAll) {
                return drone;
            }
        }

        return null;
    }

    /**
     * Log drone selection details
     */
    // logging for debugging
    private void logDroneSelection(Drone drone, ServicePoint servicePoint, int estimatedMoves, int numDispatches) {
        Capability cap = drone.getCapability();
        int maxMoves = cap.getMaxMoves();
        double maxCost = (cap.getCostInitial() + (maxMoves * cap.getCostPerMove()) + cap.getCostFinal()) / (double) numDispatches;
        logger.info("Drone ID: {}", drone.getId());
        logger.info("Drone Name: {}", drone.getName());
        logger.info("Service Point: {} (id={})", servicePoint.getName(), servicePoint.getId());
        logger.info("Capabilities:");
        logger.info("  - Max Moves: {}", maxMoves);
        logger.info("  - Capacity: {}", cap.getCapacity());
        logger.info("  - Cooling: {}", cap.getCooling());
        logger.info("  - Heating: {}", cap.getHeating());
        logger.info("Costs:");
        logger.info("  - Initial: {}", cap.getCostInitial());
        logger.info("  - Per Move: {}", cap.getCostPerMove());
        logger.info("  - Final: {}", cap.getCostFinal());
        logger.info("  - Max Possible Cost (per delivery): {}", maxCost);
        if (estimatedMoves > 0) {
            logger.info("Estimated Moves: {}", estimatedMoves);
        }
    }

    /**
     * Convert a CalcDeliveryPathResponse to GeoJSON format
     */
    public Map<String, Object> convertToGeoJson(CalcDeliveryPathResponse pathResponse) {
        if (pathResponse == null || pathResponse.getDronePaths() == null ||
                pathResponse.getDronePaths().isEmpty()) {
            return createEmptyFeatureCollection();
        }

        List<Map<String, Object>> features = new ArrayList<>();

        for (DronePath dronePath : pathResponse.getDronePaths()) {
            if (dronePath.getDeliveries() == null) continue;

            List<List<Double>> coordinates = new ArrayList<>();

            for (Delivery delivery : dronePath.getDeliveries()) {
                if (delivery.getFlightPath() == null) continue;

                for (LngLatAlt point : delivery.getFlightPath()) {
                    if (point.getLng() == null || point.getLat() == null) continue;

                    List<Double> coordinate = new ArrayList<>();
                    coordinate.add(point.getLng());
                    coordinate.add(point.getLat());
                    coordinates.add(coordinate);
                }
            }

            List<List<Double>> cleanedCoordinates = removeConsecutiveDuplicates(coordinates);

            if (cleanedCoordinates.size() >= 2) {
                Map<String, Object> geometry = new LinkedHashMap<>();
                geometry.put("type", "LineString");
                geometry.put("coordinates", cleanedCoordinates);

                Map<String, Object> properties = new LinkedHashMap<>();
                properties.put("droneId", dronePath.getDroneId());

                Map<String, Object> feature = new LinkedHashMap<>();
                feature.put("type", "Feature");
                feature.put("properties", properties);
                feature.put("geometry", geometry);

                features.add(feature);
            }
        }

        Map<String, Object> featureCollection = new LinkedHashMap<>();
        featureCollection.put("type", "FeatureCollection");
        featureCollection.put("features", features);

        return featureCollection;
    }
}
