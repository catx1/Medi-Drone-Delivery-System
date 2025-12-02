// Debug flag - set to true for development, false for production
const DEBUG = false;

// Global variables
let map, droneMarker, deliveryMarker, servicePointMarker, startMarker, flightPathLine;
let stompClient = null;
let currentOrderNumber = null;
let selectedLat = null;
let selectedLng = null;
let selectedAddress = null;

// Path fading variables
let completedPathLine = null;  // Gray path behind drone
let remainingPathLine = null;  // Blue path ahead of drone
let fullFlightPath = [];       // Store complete path
let currentPathIndex = 0;      // Track drone position in path

// No-go zone variables
let noGoZoneLayer = null;
let serviceBoundaryLayer = null;

// Original address coordinates (for distance calculation)
let originalLat = null;
let originalLng = null;

// Order lifecycle state management
let orderState = 'INITIAL'; // INITIAL → PLACING → IN_TRANSIT → ARRIVED → COLLECTED

/**
 * Change order state and update UI accordingly
 */
function setOrderState(newState) {
    if (DEBUG) console.log('🔄 Order state changing from', orderState, 'to', newState);
    orderState = newState;
    updateUIForState();
}

/**
 * Load custom Edinburgh service area boundary from JSON file
 * @returns {Promise<Array>} Array of [lng, lat] coordinate pairs
 */
async function loadEdinburghBoundary() {
    try {
        const response = await fetch('/data/edinburgh-boundary.json');
        if (!response.ok) {
            throw new Error(`Failed to load boundary: ${response.status}`);
        }
        const data = await response.json();
        if (DEBUG) console.log('✅ Loaded Edinburgh boundary from JSON file');
        return data.coordinates;
    } catch (error) {
        console.error('❌ Failed to load Edinburgh boundary:', error);
        // Fallback to empty array if file not found
        throw error;
    }
}

/**
 * Check if a point is within the Edinburgh service area
 * @param {number} lat - Latitude
 * @param {number} lng - Longitude
 * @returns {boolean} - True if point is within service area
 */
function isWithinServiceArea(lat, lng) {
    if (!serviceBoundaryLayer) {
        // If boundary not loaded yet, assume it's valid
        return true;
    }

    const point = L.latLng(lat, lng);
    const polygon = serviceBoundaryLayer;

    // Use Leaflet's built-in method to check if point is in polygon
    // We need to access the polygon's bounds and do a point-in-polygon check
    const bounds = polygon.getBounds();
    if (!bounds.contains(point)) {
        return false;
    }

    // For more accurate check, use the polygon's coordinates
    const polygonCoords = polygon.getLatLngs()[0];
    return isPointInPolygon(point, polygonCoords);
}

/**
 * Point-in-polygon algorithm (ray casting)
 */
function isPointInPolygon(point, polygon) {
    let inside = false;
    const x = point.lng;
    const y = point.lat;

    for (let i = 0, j = polygon.length - 1; i < polygon.length; j = i++) {
        const xi = polygon[i].lng;
        const yi = polygon[i].lat;
        const xj = polygon[j].lng;
        const yj = polygon[j].lat;

        const intersect = ((yi > y) !== (yj > y)) &&
            (x < (xj - xi) * (y - yi) / (yj - yi) + xi);
        if (intersect) inside = !inside;
    }

    return inside;
}

/**
 * Update UI based on current order state
 */
function updateUIForState() {
    const step1 = document.getElementById('step1');
    const step2 = document.getElementById('step2');
    const orderFormSection = document.getElementById('orderFormSection');
    const orderStatusSection = document.getElementById('orderStatusSection');
    const confirmPickupSection = document.getElementById('confirmPickupSection');

    switch(orderState) {
        case 'INITIAL':
            // Show address selection
            orderFormSection.classList.remove('hidden');
            orderStatusSection.classList.add('hidden');
            step1.classList.remove('hidden');
            step2.classList.add('hidden');
            confirmPickupSection.classList.add('hidden');
            break;

        case 'PLACING':
            // Show medication selection
            orderFormSection.classList.remove('hidden');
            orderStatusSection.classList.add('hidden');
            step1.classList.add('hidden');
            step2.classList.remove('hidden');
            confirmPickupSection.classList.add('hidden');
            break;

        case 'IN_TRANSIT':
            // Show tracking only - customer watches drone fly to them
            orderFormSection.classList.add('hidden');
            orderStatusSection.classList.remove('hidden');
            confirmPickupSection.classList.add('hidden');
            break;

        case 'ARRIVED':
            // Show collection button
            orderFormSection.classList.add('hidden');
            orderStatusSection.classList.remove('hidden');
            confirmPickupSection.classList.remove('hidden');
            break;

        case 'COLLECTED':
            // Show completion message, hide everything else
            orderFormSection.classList.add('hidden');
            orderStatusSection.classList.remove('hidden');
            confirmPickupSection.classList.add('hidden');

            // Hide the section title for collected state
            const sectionTitle = document.querySelector('.section-title');
            if (sectionTitle) {
                sectionTitle.style.display = 'none';
            }

            // Make card more compact for collected state
            const orderCard = document.querySelector('.order-card');
            if (orderCard) {
                orderCard.style.padding = '20px';
            }

            // Hide drone - customer doesn't need to see return journey
            if (droneMarker) {
                map.removeLayer(droneMarker);
                droneMarker = null;
            }
            if (flightPathLine) {
                map.removeLayer(flightPathLine);
                flightPathLine = null;
            }

            // Show completion screen (more compact)
            const statusMsg = document.getElementById('statusMessage');
            statusMsg.innerHTML = `
                <div style="text-align: center; padding: 20px 10px;">
                    <h2 style="color: #4CAF50; margin: 15px 0; font-size: 24px;">Order Collected!</h2>
                    <button class="btn" onclick="resetForNewOrder()" style="max-width: 250px; margin: 0 auto;">
                        Place New Order
                    </button>
                </div>
            `;

            // Update status badge
            const statusBadge = document.getElementById('statusBadge');
            statusBadge.innerHTML = 'COLLECTED <span class="tick-animation">✓</span>';
            statusBadge.className = 'status-badge status-collected';

            // Set progress to 100%
            document.getElementById('progressBar').style.width = '100%';
            break;
    }
}

/**
 * Reset everything for a new order
 */
function resetForNewOrder() {
    if (DEBUG) console.log('🔄 Resetting for new order');

    // Clean up map
    if (droneMarker) {
        map.removeLayer(droneMarker);
        droneMarker = null;
    }
    if (flightPathLine) {
        map.removeLayer(flightPathLine);
        flightPathLine = null;
    }
    if (completedPathLine) {
        map.removeLayer(completedPathLine);
        completedPathLine = null;
    }
    if (remainingPathLine) {
        map.removeLayer(remainingPathLine);
        remainingPathLine = null;
    }
    if (deliveryMarker) {
        map.removeLayer(deliveryMarker);
        deliveryMarker = null;
    }
    if (servicePointMarker) {
        map.removeLayer(servicePointMarker);
        servicePointMarker = null;
    }

    // Reset variables
    currentOrderNumber = null;
    selectedLat = null;
    selectedLng = null;
    selectedAddress = null;
    deliveryData = null;
    fullFlightPath = [];
    currentPathIndex = 0;

    // Reset form
    document.getElementById('address').value = '';
    document.getElementById('medication').value = '';
    document.getElementById('quantity').value = '1';
    document.getElementById('progressBar').style.width = '0%';

    // Reset map view
    map.setView([55.9445, -3.1892], 14);
    document.getElementById('addressDisplay').textContent = 'Enter your address to get started';

    // Restore section title and card padding
    const sectionTitle = document.querySelector('.section-title');
    if (sectionTitle) {
        sectionTitle.style.display = '';
    }
    const orderCard = document.querySelector('.order-card');
    if (orderCard) {
        orderCard.style.padding = '';
    }

    // Go back to initial state
    setOrderState('INITIAL');
}

// Initialize map when page loads
document.addEventListener('DOMContentLoaded', function() {
    initializeMap();
    initAutocomplete();
    loadMedications();
    connectWebSocket();

    // Set initial state
    setOrderState('INITIAL');
});

function initializeMap() {
    // 1. Initialize map, telling it NOT to create the default zoom control
    map = L.map('map', {
        zoomControl: false // <--- ADD THIS LINE
    }).setView([55.9445, -3.1892], 14);

    // 2. Add the tile layer
    L.tileLayer('https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png', {
        maxZoom: 19,
        attribution: '&copy; <a href="https://carto.com/attributions">CARTO</a> contributors'
    }).addTo(map);

    // 3. Manually add the zoom control to the 'topright' corner // <--- ADD THIS SECTION
    L.control.zoom({
        position: 'topright'
    }).addTo(map);

    // 4. Load and display the no-go zone
    initializeNoGoZone();
}

/**
 * Initialize the no-go zone visualization (areas outside Edinburgh)
 */
async function initializeNoGoZone() {
    try {
        if (DEBUG) console.log('🚫 Loading Edinburgh boundary for no-go zone...');
        const edinburghCoords = await loadEdinburghBoundary();

        // Convert coordinates from [lng, lat] to [lat, lng] for Leaflet
        const leafletCoords = edinburghCoords.map(coord => [coord[1], coord[0]]);

        // Create the Edinburgh boundary polygon (blue border)
        serviceBoundaryLayer = L.polygon(leafletCoords, {
            color: '#4689c7',
            weight: 2,
            fillColor: '#4689c7',
            fillOpacity: 0.05
        }).addTo(map);

        // Create the no-go zone (everything outside Edinburgh)
        // This creates a "hole" effect by using the outer world bounds and Edinburgh as a hole
        const worldBounds = [
            [90, -180],    // Top-left
            [90, 180],     // Top-right
            [-90, 180],    // Bottom-right
            [-90, -180],   // Bottom-left
            [90, -180]     // Close the polygon
        ];

        // Create polygon with hole - outer ring is the world, inner ring is Edinburgh
        noGoZoneLayer = L.polygon([worldBounds, leafletCoords], {
            color: 'transparent',
            fillColor: '#8bc6fc',
            fillOpacity: 0.15,
            interactive: false
        }).addTo(map);

        // Add a legend/info box
        const info = L.control({position: 'bottomleft'});
        info.onAdd = function() {
            const div = L.DomUtil.create('div', 'info legend');
            div.innerHTML = `
                <div class="legend-item">
                    <span class="legend-service-area"></span>
                    <b>Service Area</b>
                </div>
                <div>
                    <span class="legend-no-go-zone"></span>
                    <span class="legend-no-go-text">No-Go Zone</span>
                </div>
            `;
            return div;
        };
        info.addTo(map);

        if (DEBUG) console.log('No-go zone initialized successfully');
    } catch (error) {
        console.error('Failed to load Edinburgh boundary:', error);
        // Silently fail - the app can still work without the no-go zone visualization
    }
}

function validatePostcode(postcode) {
    // UK postcode regex pattern
    const postcodeRegex = /^[A-Z]{1,2}[0-9]{1,2}[A-Z]?\s?[0-9][A-Z]{2}$/i;
    return postcodeRegex.test(postcode.trim());
}

function initAutocomplete() {
    const addressInput = document.getElementById('address');
    const postcodeError = document.getElementById('postcodeError');
    const resultsContainer = document.createElement('div');

    resultsContainer.style.cssText = `
        position: fixed;
        background: white;
        border: 1px solid #e0e0e0;
        border-radius: 8px;
        max-height: 300px;
        overflow-y: auto;
        z-index: 1000;
        box-shadow: 0 4px 6px rgba(0,0,0,0.1);
        display: none;
    `;

    // Append to body to avoid clipping by parent overflow
    document.body.appendChild(resultsContainer);

    // Function to update dropdown position
    function updateDropdownPosition() {
        const rect = addressInput.getBoundingClientRect();
        resultsContainer.style.top = `${rect.bottom}px`;
        resultsContainer.style.left = `${rect.left}px`;
        resultsContainer.style.width = `${rect.width}px`;
    }

    // Auto-capitalize input
    addressInput.addEventListener('input', function() {
        this.value = this.value.toUpperCase();
    });

    let searchTimeout;

    addressInput.addEventListener('input', function() {
        clearTimeout(searchTimeout);
        const query = this.value.trim();

        // Hide error initially
        postcodeError.style.display = 'none';

        if (query.length < 2) {
            resultsContainer.style.display = 'none';
            return;
        }

        // Check if input starts with EH immediately (case-insensitive)
        if (!query.toUpperCase().startsWith('E') && query.length >= 2) {
            postcodeError.style.display = 'block';
            postcodeError.textContent = 'Postcode not in Edinburgh. Must start with EH (e.g., EH8 9AB)';
            resultsContainer.style.display = 'none';
            return;
        }

        if (query.length < 3) {
            resultsContainer.style.display = 'none';
            return;
        }

        // Validate postcode format
        if (query.length >= 5 && !validatePostcode(query)) {
            postcodeError.style.display = 'block';
            postcodeError.textContent = 'Invalid postcode format. Example: EH8 9AB';
            resultsContainer.style.display = 'none';
            return;
        }

        searchTimeout = setTimeout(() => {
            // Use backend proxy to avoid CORS issues
            fetch(`/api/v1/orders/geocode?query=${encodeURIComponent(query)}`)
                .then(r => r.json())
                .then(results => {
                    resultsContainer.innerHTML = '';

                    if (results.length === 0) {
                        resultsContainer.innerHTML = '<div style="padding: 12px; color: #999;">No results found</div>';
                        resultsContainer.style.display = 'block';
                        return;
                    }

                    results.forEach(result => {
                        const item = document.createElement('div');
                        item.style.cssText = `
                            padding: 12px;
                            cursor: pointer;
                            border-bottom: 1px solid #f0f0f0;
                            transition: background 0.2s;
                        `;
                        item.textContent = result.display_name;

                        item.addEventListener('mouseenter', () => {
                            item.style.background = '#f0f0f0';
                        });

                        item.addEventListener('mouseleave', () => {
                            item.style.background = 'white';
                        });

                        item.addEventListener('click', () => {
                            selectedLat = parseFloat(result.lat);
                            selectedLng = parseFloat(result.lon);
                            selectedAddress = result.display_name;

                            // Store original coordinates for distance calculation
                            originalLat = selectedLat;
                            originalLng = selectedLng;

                            addressInput.value = result.display_name;
                            resultsContainer.style.display = 'none';

                            if (DEBUG) {
                                console.log('Address selected:', selectedAddress);
                                console.log('Coordinates:', selectedLat, selectedLng);
                            }

                            // Center map
                            map.setView([selectedLat, selectedLng], 17);

                            // Show center marker
                            document.getElementById('centerMarker').classList.remove('hidden');

                            // Update display
                            document.getElementById('addressDisplay').textContent = 'Move map to adjust exact pickup location';

                            // Show confirm buttons
                            document.getElementById('confirmLocationBtn').classList.remove('hidden');
                            document.getElementById('changeAddressBtn').classList.remove('hidden');

                            // Track map movements
                            map.on('moveend', updateSelectedCoordinates);
                        });

                        resultsContainer.appendChild(item);
                    });

                    // Update position and show dropdown
                    updateDropdownPosition();
                    resultsContainer.style.display = 'block';
                })
                .catch(err => {
                    console.error('Geocoding error:', err);
                    resultsContainer.innerHTML = '<div style="padding: 12px; color: #f44336;">Error searching addresses</div>';
                    updateDropdownPosition();
                    resultsContainer.style.display = 'block';
                });
        }, 500);
    });

    // Update dropdown position on scroll or resize
    window.addEventListener('scroll', () => {
        if (resultsContainer.style.display === 'block') {
            updateDropdownPosition();
        }
    }, true); // Use capture to catch scroll events in child elements

    window.addEventListener('resize', () => {
        if (resultsContainer.style.display === 'block') {
            updateDropdownPosition();
        }
    });

    // Hide results when clicking outside
    document.addEventListener('click', (e) => {
        if (!addressInput.contains(e.target) && !resultsContainer.contains(e.target)) {
            resultsContainer.style.display = 'none';
        }
    });

    // Autocomplete ready
}

// Store calculated delivery data
let deliveryData = null;

function loadMedications() {
    fetch('/api/v1/orders/medications')
        .then(r => r.json())
        .then(meds => {
            const select = document.getElementById('medication');
            select.innerHTML = '<option value="">Select medication...</option>';
            meds.forEach(med => {
                const option = document.createElement('option');
                option.value = med.id;
                option.textContent = `${med.name} - ${med.description} (${med.stockQuantity} in stock)`;
                select.appendChild(option);
            });

            // Listen for medication selection
            select.addEventListener('change', onMedicationSelected);
        })
        .catch(err => {
            console.error('Failed to load medications:', err);
        });
}

function onMedicationSelected() {
    const medicationId = document.getElementById('medication').value;

    if (!medicationId) {
        clearDeliveryPreview();
        return;
    }

    if (!selectedLat || !selectedLng) {
        alert('Error: No delivery location set');
        return;
    }

    // Clear previous preview
    clearDeliveryPreview();

    // Show confirm medication buttons
    document.getElementById('confirmMedicationSection').classList.remove('hidden');
}

function confirmMedicationSelection() {
    const medicationId = document.getElementById('medication').value;

    if (!medicationId) {
        alert('Please select a medication');
        return;
    }

    if (DEBUG) {
        console.log('Confirming medication and calculating delivery:', medicationId);
        console.log('To location:', selectedLat, selectedLng);
    }

    // Hide confirm buttons and show pathfinding loader
    document.getElementById('confirmMedicationSection').classList.add('hidden');
    document.getElementById('pathfindingLoader').classList.remove('hidden');
    document.getElementById('deliveryPreview').style.display = 'none';

    // Calculate delivery path
    fetch(`/api/v1/drone/calculate-delivery?medicationId=${medicationId}&targetLat=${selectedLat}&targetLng=${selectedLng}`, {
        method: 'POST'
    })
        .then(r => r.json())
        .then(data => {
            if (DEBUG) console.log('Delivery calculation response:', data);
            if (data.success) {
                deliveryData = data;
                if (DEBUG) console.log('Delivery data saved:', deliveryData);
                showDeliveryPreview(data);

                // Hide pathfinding loader, show Place Order section
                document.getElementById('pathfindingLoader').classList.add('hidden');
                document.getElementById('placeOrderSection').classList.remove('hidden');
            } else {
                // Hide loader on error, show confirm buttons again
                document.getElementById('pathfindingLoader').classList.add('hidden');
                document.getElementById('confirmMedicationSection').classList.remove('hidden');
                alert('Error calculating path: ' + data.error);
                document.getElementById('deliveryPreview').style.display = 'none';
            }
        })
        .catch(err => {
            console.error('Failed to calculate delivery:', err);
            // Hide loader on error, show confirm buttons again
            document.getElementById('pathfindingLoader').classList.add('hidden');
            document.getElementById('confirmMedicationSection').classList.remove('hidden');
            alert('Failed to calculate delivery path');
            document.getElementById('deliveryPreview').style.display = 'none';
        });
}

function showDeliveryPreview(data) {
    // Store the full path for later use (path fading)
    fullFlightPath = data.path.map(p => [p.lat, p.lng]);
    currentPathIndex = 0;

    // Draw FULL path in blue (preview mode)
    if (flightPathLine) {
        map.removeLayer(flightPathLine);
    }

    flightPathLine = L.polyline(fullFlightPath, {
        color: '#2196F3',
        weight: 4,
        opacity: 0.7,
        smoothFactor: 1
    }).addTo(map);

    // Add service point marker (orange for preview)
    if (servicePointMarker) {
        map.removeLayer(servicePointMarker);
    }

    servicePointMarker = L.marker([data.servicePoint.lat, data.servicePoint.lng], {
        icon: L.icon({
            iconUrl: '/images/service_marker.png',
            iconSize: [26, 38],
            iconAnchor: [13, 38],
            popupAnchor: [0, -38]
        })
    }).addTo(map).bindPopup(`<b>${data.servicePoint.name}</b><br>Service Point`);

    // Fit map to show full path
    map.fitBounds(flightPathLine.getBounds(), { padding: [50, 50] });

    // Update delivery preview
    const deliveryPreview = document.getElementById('deliveryPreview');
    deliveryPreview.innerHTML = `
        <div style="background: #e3f2fd; padding: 15px; border-radius: 8px; margin: 15px 0;">
            <div><strong>Delivery Preview</strong></div>
            <div style="margin-top: 10px;">
                <div>Service Point: <strong>${data.servicePoint.name}</strong></div>
                <div>Assigned Drone: <strong>${data.assignedDrone}</strong></div>
                <div>Requires Refrigeration: <strong>${data.requiresRefrigeration ? 'Yes' : 'No'}</strong></div>
            </div>
        </div>
    `;
    deliveryPreview.style.display = 'block';
}

function clearDeliveryPreview() {
    if (flightPathLine) {
        map.removeLayer(flightPathLine);
        flightPathLine = null;
    }
    if (completedPathLine) {
        map.removeLayer(completedPathLine);
        completedPathLine = null;
    }
    if (remainingPathLine) {
        map.removeLayer(remainingPathLine);
        remainingPathLine = null;
    }
    if (servicePointMarker) {
        map.removeLayer(servicePointMarker);
        servicePointMarker = null;
    }
    const deliveryPreview = document.getElementById('deliveryPreview');
    if (deliveryPreview) {
        deliveryPreview.style.display = 'none';
    }

    // Hide all sections
    document.getElementById('confirmMedicationSection').classList.add('hidden');
    document.getElementById('pathfindingLoader').classList.add('hidden');
    document.getElementById('placeOrderSection').classList.add('hidden');

    deliveryData = null;
    fullFlightPath = [];
    currentPathIndex = 0;
}

function updateSelectedCoordinates() {
    const center = map.getCenter();
    selectedLat = center.lat;
    selectedLng = center.lng;
    if (DEBUG) console.log('Location adjusted to:', selectedLat, selectedLng);

    // Update visual feedback for service area
    const isInServiceArea = isWithinServiceArea(selectedLat, selectedLng);
    const addressDisplay = document.getElementById('addressDisplay');
    const centerMarker = document.getElementById('centerMarker');

    if (isInServiceArea) {
        addressDisplay.textContent = 'Move map to adjust exact pickup location';
        addressDisplay.style.background = 'rgba(76, 175, 80, 0.95)'; // Green
        addressDisplay.style.color = 'white';
        if (centerMarker) {
            centerMarker.style.filter = 'none';
        }
    } else {
        addressDisplay.textContent = '⚠️ Outside service area - Move to Edinburgh';
        addressDisplay.style.background = 'rgba(244, 67, 54, 0.95)'; // Red
        addressDisplay.style.color = 'white';
        if (centerMarker) {
            centerMarker.style.filter = 'hue-rotate(120deg) saturate(3)'; // Make it more red
        }
    }
}

/**
 * Calculate distance between two coordinates using Haversine formula
 * @returns distance in meters
 */
function calculateDistance(lat1, lng1, lat2, lng2) {
    const R = 6371e3; // Earth's radius in meters
    const phi1 = lat1 * Math.PI / 180;
    const phi2 = lat2 * Math.PI / 180;
    const deltaPhi = (lat2 - lat1) * Math.PI / 180;
    const deltaLambda = (lng2 - lng1) * Math.PI / 180;

    const a = Math.sin(deltaPhi / 2) * Math.sin(deltaPhi / 2) +
              Math.cos(phi1) * Math.cos(phi2) *
              Math.sin(deltaLambda / 2) * Math.sin(deltaLambda / 2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

    return R * c; // Distance in meters
}

function confirmLocation() {
    // Validate address is set
    if (!selectedAddress || !selectedLat || !selectedLng) {
        alert('Please select an address from the dropdown first');
        return;
    }

    // Save the final coordinates
    const center = map.getCenter();
    selectedLat = center.lat;
    selectedLng = center.lng;

    // Calculate distance moved from original address
    const distanceMoved = calculateDistance(originalLat, originalLng, selectedLat, selectedLng);
    if (DEBUG) console.log('Distance moved from original address:', distanceMoved.toFixed(2), 'meters');

    // If moved more than 10m, fetch new address
    if (distanceMoved > 10) {
        if (DEBUG) console.log('Moved >10m, fetching new address...');

        // Show loading state
        document.getElementById('addressDisplay').textContent = 'Updating address...';

        fetch(`/api/v1/orders/reverse-geocode?lat=${selectedLat}&lng=${selectedLng}`)
            .then(r => r.json())
            .then(result => {
                if (result.display_name) {
                    selectedAddress = result.display_name;
                    document.getElementById('address').value = selectedAddress;
                    if (DEBUG) console.log('Address updated to:', selectedAddress);
                }
                finishLocationConfirmation();
            })
            .catch(err => {
                console.error('Reverse geocode error:', err);
                // Continue anyway with old address
                finishLocationConfirmation();
            });
    } else {
        // Moved less than 10m, keep original address
        if (DEBUG) console.log('Moved <10m, keeping original address');
        finishLocationConfirmation();
    }
}

/**
 * Complete the location confirmation process
 */
function finishLocationConfirmation() {
    // Check if location is within service area
    if (!isWithinServiceArea(selectedLat, selectedLng)) {
        alert('⚠️ This location is outside our Edinburgh service area!\n\nPlease select an address within Edinburgh city limits.');
        return;
    }

    if (DEBUG) console.log('Location confirmed:', selectedAddress, 'at', selectedLat, selectedLng);

    // Hide center marker
    document.getElementById('centerMarker').classList.add('hidden');

    // Remove map move listener
    map.off('moveend', updateSelectedCoordinates);

    // Hide confirm buttons
    document.getElementById('confirmLocationBtn').classList.add('hidden');
    document.getElementById('changeAddressBtn').classList.add('hidden');

    // Clear any previous delivery preview/data so it recalculates
    clearDeliveryPreview();

    // Create permanent delivery marker at confirmed location
    if (deliveryMarker) {
        map.removeLayer(deliveryMarker);
    }

    deliveryMarker = L.marker([selectedLat, selectedLng], {
        icon: L.icon({
            iconUrl: '/images/loc_marker.png',
            iconSize: [26, 38],
            iconAnchor: [13, 38],
            popupAnchor: [0, -38]
        })
    }).addTo(map);

    // Update address display
    const addressDisplay = document.getElementById('addressDisplay');
    addressDisplay.innerHTML = '<strong>Delivering to:</strong><br>' + selectedAddress;
    addressDisplay.style.background = ''; // Reset to default
    addressDisplay.style.color = ''; // Reset to default

    // Move to step 2 - medication selection
    setOrderState('PLACING');
    if (DEBUG) console.log('Moved to Step 2 - medication selection');
}

function changeAddress() {
    // Hide center marker
    document.getElementById('centerMarker').classList.add('hidden');

    // Remove map move listener
    map.off('moveend', updateSelectedCoordinates);

    // Hide confirm buttons
    document.getElementById('confirmLocationBtn').classList.add('hidden');
    document.getElementById('changeAddressBtn').classList.add('hidden');

    // Clear the address input
    document.getElementById('address').value = '';

    // Reset coordinates
    selectedLat = null;
    selectedLng = null;
    selectedAddress = null;

    // Clear delivery preview and data
    clearDeliveryPreview();

    // Remove delivery marker if exists
    if (deliveryMarker) {
        map.removeLayer(deliveryMarker);
        deliveryMarker = null;
    }

    const addressDisplay = document.getElementById('addressDisplay');
    addressDisplay.textContent = 'Enter your address to get started';
    addressDisplay.style.background = ''; // Reset to default
    addressDisplay.style.color = ''; // Reset to default

    // Reset map view
    map.setView([55.9445, -3.1892], 14);
}

function backToStep1() {
    // Remove delivery marker
    if (deliveryMarker) {
        map.removeLayer(deliveryMarker);
        deliveryMarker = null;
    }

    // Clear delivery preview and data
    clearDeliveryPreview();

    // Reset medication selection
    document.getElementById('medication').value = '';

    // Show center marker again
    document.getElementById('centerMarker').classList.remove('hidden');

    // Show confirm buttons
    document.getElementById('confirmLocationBtn').classList.remove('hidden');
    document.getElementById('changeAddressBtn').classList.remove('hidden');

    // Re-enable map movement listener
    map.on('moveend', updateSelectedCoordinates);

    // Center on last selected location
    if (selectedLat && selectedLng) {
        map.setView([selectedLat, selectedLng], 17);
    }

    document.getElementById('addressDisplay').textContent = 'Move map to adjust exact pickup location';

    // Go back to initial state (address confirmation)
    setOrderState('INITIAL');
}

function placeOrder() {
    const medicationId = document.getElementById('medication').value;
    const quantity = parseInt(document.getElementById('quantity').value);
    const quantityError = document.getElementById('quantityError');

    if (!medicationId) {
        alert('Please select a medication');
        return;
    }

    // Validate quantity
    if (isNaN(quantity) || quantity < 1 || quantity > 5) {
        quantityError.style.display = 'block';
        alert('Quantity must be between 1 and 5');
        return;
    }
    quantityError.style.display = 'none';

    if (!deliveryData) {
        alert('Please wait for delivery calculation to complete');
        return;
    }

    if (!selectedLat || !selectedLng || !selectedAddress) {
        alert('Please enter and confirm your address first');
        return;
    }

    // Show loading state
    document.getElementById('statusMessage').textContent = 'Placing order...';

    // Store a copy of deliveryData to preserve it
    const savedDeliveryData = { ...deliveryData };

    // Debug logging
    if (DEBUG) {
        console.log('Placing order with delivery data:', savedDeliveryData);
        console.log('Distance:', savedDeliveryData.distanceKm);
        console.log('ETA:', savedDeliveryData.etaMinutes);
    }

    // Create order with address, coordinates, pre-calculated path, AND assigned drone
    // Send path in request body to avoid URL length limits
    fetch('/api/v1/orders/create-with-address', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            address: selectedAddress,
            lat: selectedLat,
            lng: selectedLng,
            medicationId: medicationId,
            quantity: quantity,
            calculatedPath: savedDeliveryData.path,  // Send path array directly in body
            assignedDroneId: savedDeliveryData.assignedDrone  // Send assigned drone ID
        })
    })
        .then(r => r.json())
        .then(data => {
            if (data.success) {
                // Order created successfully
                currentOrderNumber = data.orderNumber;


                // Update UI with order details
                document.getElementById('orderNumber').textContent = data.orderNumber;

                // Show delivery info with saved data (includes null checks)
                const statusMsg = document.getElementById('statusMessage');
                statusMsg.innerHTML = `
                    <div style="background: #e8f5e9; padding: 15px; border-radius: 8px; margin: 15px 0;">
                        <div><strong>Live Tracking</strong></div>
                        <div style="margin-top: 10px;">
                            <div>From: <strong>${savedDeliveryData.servicePoint ? savedDeliveryData.servicePoint.name : 'Unknown'}</strong></div>
                        </div>
                    </div>
                    <div style="text-align: center; color: #2196F3; font-weight: 600;">
                        Drone dispatched - track it live on the map!
                    </div>
                `;

                updateStatusBadge('FLYING');

                // Draw the planned flight path on the map
                drawFlightPath(savedDeliveryData);

                // Change state to IN_TRANSIT - customer watches drone fly to them
                setOrderState('IN_TRANSIT');

                // WebSocket will handle live updates - no local simulation needed
            } else {
                alert('Error: ' + data.error);
            }
        })
        .catch(error => {
            console.error('Failed to place order:', error);
            alert('Failed to place order: ' + error);
        });
}

function drawFlightPath(deliveryData) {
    // Store the full path for fading effect
    fullFlightPath = deliveryData.path.map(p => [p.lat, p.lng]);
    currentPathIndex = 0;

    // Draw the flight path on the map (solid blue for live tracking)
    if (flightPathLine) {
        map.removeLayer(flightPathLine);
    }

    flightPathLine = L.polyline(fullFlightPath, {
        color: '#2196F3',
        weight: 4,
        opacity: 0.7,
        smoothFactor: 1
    }).addTo(map);

    // Keep the service point marker from earlier
    // Keep the delivery marker from earlier

    // Fit map to show full path
    map.fitBounds(flightPathLine.getBounds(), { padding: [50, 50] });

    // Create drone marker at start position (will be updated by WebSocket)
    const startPos = deliveryData.path[0];
    if (!droneMarker) {
        droneMarker = L.marker([startPos.lat, startPos.lng], {
            icon: L.icon({
                iconUrl: '/images/drone_icon.png',
                iconSize: [29, 29],
                iconAnchor: [16.5, 14.5],
                popupAnchor: [0, -14.5]
            })
        }).addTo(map).bindPopup(`<b>${deliveryData.assignedDrone}</b><br>Flying...`);
    }
}



function connectWebSocket() {
    // Initialize WebSocket connection
    const socket = new SockJS('/ws');
    stompClient = Stomp.over(socket);

    // Enable debug logging for STOMP
    stompClient.debug = function(msg) {
        if (DEBUG) console.log('STOMP:', msg);
    };

    stompClient.connect({}, function(frame) {
        if (DEBUG) {
            console.log('✅ WebSocket CONNECTED successfully!', frame);
            console.log('📡 Subscribing to /topic/drone/position...');
        }

        // Subscribe to drone positions
        const subscription = stompClient.subscribe('/topic/drone/position', function(message) {
            if (DEBUG) console.log('📨 Received WebSocket message:', message.body);
            const position = JSON.parse(message.body);
            if (DEBUG) console.log('📍 Parsed position:', position);
            updateDronePosition(position);
        });

        if (DEBUG) console.log('✅ Successfully subscribed to drone position updates!', subscription);
    }, function(error) {
        console.error('❌ WebSocket connection error:', error);
        console.error('WebSocket connection failed, retrying in 3 seconds...');
        setTimeout(connectWebSocket, 3000);
    });
}

function updateDronePosition(position) {
    if (DEBUG) console.log('🔍 Update check - Received order:', position.orderNumber, 'Current order:', currentOrderNumber, 'State:', orderState);

    // Strict filtering: Only show drone if ALL conditions are met:
    // 1. We have an active order (not null)
    // 2. The position is for OUR order (exact match)
    // 3. We're in IN_TRANSIT state (actively tracking)
    // 4. Order hasn't been collected yet

    if (!currentOrderNumber) {
        if (DEBUG) console.log('⚠️ Ignoring update - no active order');
        return;
    }

    if (position.orderNumber !== currentOrderNumber) {
        if (DEBUG) console.log('⚠️ Ignoring update - not our order');
        return;
    }

    if (orderState !== 'IN_TRANSIT' && orderState !== 'ARRIVED') {
        if (DEBUG) console.log('⚠️ Ignoring update - not in tracking state');
        return;
    }

    if (DEBUG) console.log('📍 Drone position update accepted:', position);

    // Create or update drone marker
    if (!droneMarker) {
        droneMarker = L.marker([position.lat, position.lng], {
            icon: L.icon({
                iconUrl: '/images/drone_icon.png',
                iconSize: [29, 29],
                iconAnchor: [16.5, 14.5],
                popupAnchor: [0, -14.5]
            })
        }).addTo(map);
    }

    droneMarker.setLatLng([position.lat, position.lng]);

    // Update popup content
    if (droneMarker.getPopup()) {
        droneMarker.getPopup().setContent(`<b>${position.droneId}</b><br>Status: ${position.status}`);
    } else {
        droneMarker.bindPopup(`<b>${position.droneId}</b><br>Status: ${position.status}`);
    }

    // Update fading path effect
    updateFadingPath([position.lat, position.lng]);

    // Update progress bar
    if (position.percentComplete !== undefined) {
        document.getElementById('progressBar').style.width = position.percentComplete + '%';
    }

    // Update status badge
    const statusBadge = document.getElementById('statusBadge');
    if (statusBadge) {
        statusBadge.textContent = position.status;
        statusBadge.className = `status-badge status-${position.status.toLowerCase()}`;
    }

    // If drone has arrived and we're still in transit, change to ARRIVED state
    if (position.status === 'ARRIVED' && orderState === 'IN_TRANSIT') {
        if (DEBUG) console.log('✅ Drone has arrived! Showing collection button');
        setOrderState('ARRIVED');

        // Show browser notification if permission granted
        if ('Notification' in window && Notification.permission === 'granted') {
            new Notification('MediDrone Delivery 🚁', {
                body: 'Your medication has arrived! Click "Order Collected" when you\'ve retrieved it.',
                icon: '/images/drone_icon.png'
            });
        }

        // Center map on delivery location
        map.panTo([position.lat, position.lng], {
            animate: true,
            duration: 0.25
        });
    } else if (orderState === 'IN_TRANSIT') {
        // Pan to follow drone during flight (only if still in transit)
        map.panTo([position.lat, position.lng], {
            animate: true,
            duration: 0.25
        });
    }
}

/**
 * Update fading path effect - gray behind drone, blue ahead
 */
function updateFadingPath(dronePosition) {
    if (!fullFlightPath || fullFlightPath.length === 0) {
        return;
    }

    // Find closest point on path to drone's current position
    let closestIndex = 0;
    let minDistance = Infinity;

    fullFlightPath.forEach((point, index) => {
        const distance = Math.sqrt(
            Math.pow(point[0] - dronePosition[0], 2) +
            Math.pow(point[1] - dronePosition[1], 2)
        );
        if (distance < minDistance) {
            minDistance = distance;
            closestIndex = index;
        }
    });

    currentPathIndex = closestIndex;

    // Split path into completed (behind) and remaining (ahead)
    const completedPath = fullFlightPath.slice(0, closestIndex + 1);
    const remainingPath = fullFlightPath.slice(closestIndex);

    // Remove old path lines
    if (completedPathLine) {
        map.removeLayer(completedPathLine);
    }
    if (remainingPathLine) {
        map.removeLayer(remainingPathLine);
    }
    if (flightPathLine) {
        map.removeLayer(flightPathLine);  // Remove preview path
        flightPathLine = null;
    }

    // Draw COMPLETED path (gray, faded)
    if (completedPath.length > 1) {
        completedPathLine = L.polyline(completedPath, {
            color: '#9E9E9E',      // Gray
            weight: 3,
            opacity: 0.4,          // Faded
            smoothFactor: 1,
            dashArray: '5, 5'      // Dashed line
        }).addTo(map);
    }

    // Draw REMAINING path (blue, bright)
    if (remainingPath.length > 1) {
        remainingPathLine = L.polyline(remainingPath, {
            color: '#2196F3',      // Blue
            weight: 4,
            opacity: 0.8,          // Bright
            smoothFactor: 1
        }).addTo(map);
    }
}

function updateStatusBadge(status) {
    if (DEBUG) console.log('🏷️ Updating status badge to:', status);

    const badge = document.getElementById('statusBadge');
    if (badge) {
        badge.textContent = status.replace('_', ' ');
        badge.className = 'status-badge status-' + status.toLowerCase().replace('_', '-');
    }
}

function confirmPickup() {
    if (!currentOrderNumber) {
        alert('Error: No active order');
        return;
    }

    if (DEBUG) console.log('📦 Confirming pickup for order:', currentOrderNumber);

    fetch('/api/v1/orders/confirm-pickup-by-order?orderNumber=' + currentOrderNumber, {
        method: 'POST'
    })
        .then(r => {
            if (r.ok) {
                return r.json();
            } else {
                return r.text().then(text => {
                    throw new Error(text);
                });
            }
        })
        .then(data => {
            if (data.success) {
                if (DEBUG) console.log('Order collected successfully!');
                // Change to COLLECTED state - shows completion screen, hides drone
                setOrderState('COLLECTED');
            } else {
                alert('Error: ' + data.error);
            }
        })
        .catch(error => {
            console.error('Failed to confirm pickup:', error);
            alert('Failed to confirm pickup: ' + error.message);
        });
}

function resetOrder() {
    // Use the new state-managed reset function
    resetForNewOrder();
}

