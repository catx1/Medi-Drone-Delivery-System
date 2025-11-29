package org.example.cw3ilp.service;

import com.google.maps.GeoApiContext;
import com.google.maps.GeocodingApi;
import com.google.maps.model.GeocodingResult;
import com.google.maps.model.LatLng;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
public class GeocodingService {
    @Value("${google.maps.api.key:GOOGLE_MAPS_API_KEY}")
    private String apiKey;

    private GeoApiContext context;

    @PostConstruct
    public void init() {
        context = new GeoApiContext.Builder()
                .apiKey(apiKey)
                .build();
    }

    public Map<String, Double> geocodeAddress(String address) {
        try {
            GeocodingResult[] results = GeocodingApi.geocode(context,
                    address + ", Edinburgh, Scotland").await();

            if (results.length > 0) {
                LatLng location = results[0].geometry.location;
                return Map.of(
                        "lat", location.lat,
                        "lng", location.lng
                );
            }

            throw new RuntimeException("Address not found");

        } catch (Exception e) {
            log.error("Geocoding failed", e);
            throw new RuntimeException("Failed to geocode address: " + e.getMessage());
        }
    }
}
