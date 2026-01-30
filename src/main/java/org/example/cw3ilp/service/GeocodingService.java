//package org.example.cw3ilp.service;
//
//import com.google.maps.GeoApiContext;
//import com.google.maps.GeocodingApi;
//import com.google.maps.model.GeocodingResult;
//import com.google.maps.model.LatLng;
//import jakarta.annotation.PostConstruct;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.stereotype.Service;
//
//import java.util.Map;
//
//@Service
//@Slf4j
//public class GeocodingService {
//    @Value("${google.maps.api.key:GOOGLE_MAPS_API_KEY}")
//    private String apiKey;
//
//    private GeoApiContext context;
//    private boolean demoMode = false;
//
//    @PostConstruct
//    public void init() {
//        // Check if API key is missing or placeholder
//        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("GOOGLE_MAPS_API_KEY")) {
//            log.warn("No valid Google Maps API key found - running in DEMO MODE with hardcoded coordinates");
//                    demoMode = true;
//        } else {
//            context = new GeoApiContext.Builder()
//                    .apiKey(apiKey)
//                    .build();
//        }
//    }
//
//    public Map<String, Double> geocodeAddress(String address) {
//        if (demoMode) {
//            return getDemoCoordinates(address);
//        }
//
//        try {
//            GeocodingResult[] results = GeocodingApi.geocode(context,
//                    address + ", Edinburgh, Scotland").await();
//
//            if (results.length > 0) {
//                LatLng location = results[0].geometry.location;
//                return Map.of(
//                        "lat", location.lat,
//                        "lng", location.lng
//                );
//            }
//
//            throw new RuntimeException("Address not found");
//
//        } catch (Exception e) {
//            log.error("Geocoding failed", e);
//            throw new RuntimeException("Failed to geocode address: " + e.getMessage());
//        }
//    }
//
//    private Map<String, Double> getDemoCoordinates(String address) {
//        String lower = address.toLowerCase();
//
//        // Return specific coordinates for known Edinburgh locations
//        if (lower.contains("appleton") || lower.contains("tower")) {
//            return Map.of("lat", 55.9445, "lng", -3.1869);
//        } else if (lower.contains("castle") || lower.contains("royal mile")) {
//            return Map.of("lat", 55.9496, "lng", -3.1909);
//        } else if (lower.contains("meadows")) {
//            return Map.of("lat", 55.9398, "lng", -3.1914);
//        } else if (lower.contains("princes") || lower.contains("street")) {
//            return Map.of("lat", 55.9521, "lng", -3.1965);
//        }
//
//        // Default: central Edinburgh with slight random offset for variety
//        double baseLat = 55.9445;
//        double baseLng = -3.1869;
//        double offset = (address.hashCode() % 100) * 0.0001;
//
//        return Map.of("lat", baseLat + offset, "lng", baseLng + offset);
//    }
//}
