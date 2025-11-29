// Global variables
let map, droneMarker, deliveryMarker, servicePointMarker, startMarker, flightPathLine;
let stompClient = null;
let currentOrderNumber = null;
let selectedLat = null;
let selectedLng = null;
let selectedAddress = null;

// Order lifecycle state management
let orderState = 'INITIAL'; // INITIAL → PLACING → IN_TRANSIT → ARRIVED → COLLECTED

/**
 * Change order state and update UI accordingly
 */
function setOrderState(newState) {
    console.log('🔄 Order state changing from', orderState, 'to', newState);
    orderState = newState;
    updateUIForState();
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

            // Hide drone - customer doesn't need to see return journey
            if (droneMarker) {
                map.removeLayer(droneMarker);
                droneMarker = null;
            }
            if (flightPathLine) {
                map.removeLayer(flightPathLine);
                flightPathLine = null;
            }

            // Show completion screen
            const statusMsg = document.getElementById('statusMessage');
            statusMsg.innerHTML = `
                <div style="text-align: center; padding: 40px 20px;">
                    <div style="font-size: 80px; margin-bottom: 20px;">✅</div>
                    <h2 style="color: #4CAF50; margin: 20px 0; font-size: 28px;">Order Collected!</h2>
                    <p style="color: #666; font-size: 16px; margin-bottom: 30px;">
                        Thank you for using MediDrone delivery service.
                    </p>
                    <button class="btn" onclick="resetForNewOrder()" style="max-width: 300px; margin: 0 auto;">
                        Place New Order
                    </button>
                </div>
            `;

            // Update status badge
            const statusBadge = document.getElementById('statusBadge');
            statusBadge.textContent = 'COLLECTED';
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
    console.log('🔄 Resetting for new order');

    // Clean up map
    if (droneMarker) {
        map.removeLayer(droneMarker);
        droneMarker = null;
    }
    if (flightPathLine) {
        map.removeLayer(flightPathLine);
        flightPathLine = null;
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

    // Reset form
    document.getElementById('address').value = '';
    document.getElementById('medication').value = '';
    document.getElementById('quantity').value = '1';
    document.getElementById('progressBar').style.width = '0%';

    // Reset map view
    map.setView([55.9445, -3.1892], 14);
    document.getElementById('addressDisplay').textContent = 'Enter your address to get started';

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
    // Initialize map centered on Edinburgh with CARTO light theme
    map = L.map('map').setView([55.9445, -3.1892], 14);
    L.tileLayer('https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png', {
        maxZoom: 19,
        attribution: '&copy; <a href="https://carto.com/attributions">CARTO</a> contributors'
    }).addTo(map);
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
        position: absolute;
        background: white;
        border: 1px solid #e0e0e0;
        border-radius: 8px;
        max-height: 300px;
        overflow-y: auto;
        z-index: 1000;
        box-shadow: 0 4px 6px rgba(0,0,0,0.1);
        display: none;
        width: 100%;
        margin-top: 2px;
    `;

    addressInput.parentElement.style.position = 'relative';
    addressInput.parentElement.appendChild(resultsContainer);

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

                            addressInput.value = result.display_name;
                            resultsContainer.style.display = 'none';

                            console.log('Address selected:', selectedAddress);
                            console.log('Coordinates:', selectedLat, selectedLng);

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

                    resultsContainer.style.display = 'block';
                })
                .catch(err => {
                    console.error('Geocoding error:', err);
                    resultsContainer.innerHTML = '<div style="padding: 12px; color: #f44336;">Error searching addresses</div>';
                    resultsContainer.style.display = 'block';
                });
        }, 500);
    });

    // Hide results when clicking outside
    document.addEventListener('click', (e) => {
        if (!addressInput.contains(e.target) && !resultsContainer.contains(e.target)) {
            resultsContainer.style.display = 'none';
        }
    });

    console.log('OpenStreetMap autocomplete ready! (Free, no API key needed)');
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

    console.log('Calculating delivery for medication:', medicationId);
    console.log('To location:', selectedLat, selectedLng);

    // Clear previous preview first
    clearDeliveryPreview();

    // Show loading state
    document.getElementById('deliveryPreview').textContent = 'Calculating delivery path...';
    document.getElementById('deliveryPreview').style.display = 'block';

    // Calculate delivery path
    fetch(`/api/v1/drone/calculate-delivery?medicationId=${medicationId}&targetLat=${selectedLat}&targetLng=${selectedLng}`, {
        method: 'POST'
    })
        .then(r => r.json())
        .then(data => {
            console.log('Delivery calculation response:', data);
            if (data.success) {
                deliveryData = data;
                console.log('Delivery data saved:', deliveryData);
                showDeliveryPreview(data);
            } else {
                alert('Error calculating path: ' + data.error);
                document.getElementById('deliveryPreview').style.display = 'none';
            }
        })
        .catch(err => {
            console.error('Failed to calculate delivery:', err);
            alert('Failed to calculate delivery path');
            document.getElementById('deliveryPreview').style.display = 'none';
        });
}

function showDeliveryPreview(data) {
    // Draw path on map
    if (flightPathLine) {
        map.removeLayer(flightPathLine);
    }

    const pathCoords = data.path.map(p => [p.lat, p.lng]);
    flightPathLine = L.polyline(pathCoords, {
        color: '#2196F3',
        weight: 3,
        opacity: 0.7
    }).addTo(map);

    // Add service point marker
    if (servicePointMarker) {
        map.removeLayer(servicePointMarker);
    }

    servicePointMarker = L.marker([data.servicePoint.lat, data.servicePoint.lng], {
        icon: L.icon({
            iconUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-green.png',
            iconSize: [25, 41],
            iconAnchor: [12.5, 41],
            popupAnchor: [0, -41]
        })
    }).addTo(map).bindPopup(`<b>${data.servicePoint.name}</b><br>Service Point`);

    // Fit map to show full path
    map.fitBounds(flightPathLine.getBounds(), { padding: [50, 50] });

    // Calculate distance in meters
    const distanceMeters = Math.round(data.distanceKm * 1000);
    const distanceDisplay = `${data.distanceKm} km (${distanceMeters.toLocaleString()} m)`;

    // Calculate ETA in minutes and seconds
    const totalSeconds = Math.round(data.etaMinutes * 60);
    const etaMinutes = Math.floor(totalSeconds / 60);
    const etaSeconds = totalSeconds % 60;
    const etaDisplay = etaSeconds > 0
        ? `${etaMinutes} minutes ${etaSeconds} seconds`
        : `${etaMinutes} minutes`;

    // Update delivery preview
    const deliveryPreview = document.getElementById('deliveryPreview');
    deliveryPreview.innerHTML = `
        <div style="background: #e3f2fd; padding: 15px; border-radius: 8px; margin: 15px 0;">
            <div><strong>Delivery Preview</strong></div>
            <div style="margin-top: 10px;">
                <div>Service Point: <strong>${data.servicePoint.name}</strong></div>
                <div>Assigned Drone: <strong>${data.assignedDrone}</strong></div>
                <div>Requires Refrigeration: <strong>${data.requiresRefrigeration ? 'Yes' : 'No'}</strong></div>
                <div>Distance: <strong>${distanceDisplay}</strong></div>
                <div>ETA: <strong>${etaDisplay}</strong></div>
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
    if (servicePointMarker) {
        map.removeLayer(servicePointMarker);
        servicePointMarker = null;
    }
    const deliveryPreview = document.getElementById('deliveryPreview');
    if (deliveryPreview) {
        deliveryPreview.style.display = 'none';
    }
    deliveryData = null;
}

function updateSelectedCoordinates() {
    const center = map.getCenter();
    selectedLat = center.lat;
    selectedLng = center.lng;
    console.log('Location adjusted to:', selectedLat, selectedLng);
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

    console.log('Location confirmed:', selectedAddress, 'at', selectedLat, selectedLng);

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
            iconUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-red.png',
            iconSize: [25, 41],
            iconAnchor: [12.5, 41],
            popupAnchor: [0, -41]
        })
    }).addTo(map);

    // Update address display
    document.getElementById('addressDisplay').innerHTML = '<strong>Delivering to:</strong><br>' + selectedAddress;

    // Move to step 2 - medication selection
    setOrderState('PLACING');
    console.log('Moved to Step 2 - medication selection');
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

    document.getElementById('addressDisplay').textContent = 'Enter your address to get started';

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
    const quantity = document.getElementById('quantity').value;

    if (!medicationId) {
        alert('Please select a medication');
        return;
    }

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
    console.log('Placing order with delivery data:', savedDeliveryData);
    console.log('Distance:', savedDeliveryData.distanceKm);
    console.log('ETA:', savedDeliveryData.etaMinutes);

    // Create order with address AND coordinates
    fetch(`/api/v1/orders/create-with-address?address=${encodeURIComponent(selectedAddress)}&lat=${selectedLat}&lng=${selectedLng}&medicationId=${medicationId}&quantity=${quantity}`, {
        method: 'POST'
    })
        .then(r => r.json())
        .then(data => {
            if (data.success) {
                // Order created successfully
                currentOrderNumber = data.orderNumber;


                // Update UI with order details
                document.getElementById('orderNumber').textContent = data.orderNumber;

                // Calculate distance in meters
                const distanceKm = savedDeliveryData.distanceKm || 0;
                const distanceMeters = Math.round(distanceKm * 1000);
                const distanceDisplay = `${distanceKm} km (${distanceMeters.toLocaleString()} m)`;

                // Calculate ETA in minutes and seconds
                const etaMinutesValue = savedDeliveryData.etaMinutes || 0;
                const totalSeconds = Math.round(etaMinutesValue * 60);
                const etaMinutes = Math.floor(totalSeconds / 60);
                const etaSeconds = totalSeconds % 60;
                const etaDisplay = etaSeconds > 0
                    ? `${etaMinutes} minutes ${etaSeconds} seconds`
                    : `${etaMinutes} minutes`;

                // Show delivery info with saved data (includes null checks)
                const statusMsg = document.getElementById('statusMessage');
                statusMsg.innerHTML = `
                    <div style="background: #e8f5e9; padding: 15px; border-radius: 8px; margin: 15px 0;">
                        <div><strong>Live Tracking</strong></div>
                        <div style="margin-top: 10px;">
                            <div>Drone: <strong>${savedDeliveryData.assignedDrone || 'Unknown'}</strong></div>
                            <div>From: <strong>${savedDeliveryData.servicePoint ? savedDeliveryData.servicePoint.name : 'Unknown'}</strong></div>
                            <div>Distance: <strong>${distanceDisplay}</strong></div>
                            <div>ETA: <strong>${etaDisplay}</strong></div>
                        </div>
                    </div>
                    <div style="text-align: center; color: #2196F3; font-weight: 600;">
                        Drone dispatched! Watch it fly on the map...
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
    // Draw the flight path on the map (just the line, no animation)
    if (flightPathLine) {
        map.removeLayer(flightPathLine);
    }

    const pathCoords = deliveryData.path.map(p => [p.lat, p.lng]);
    flightPathLine = L.polyline(pathCoords, {
        color: '#2196F3',
        weight: 3,
        opacity: 0.7,
        dashArray: '10, 10'
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
                iconSize: [40, 35],
                iconAnchor: [20, 17.5],
                popupAnchor: [0, -17.5]
            })
        }).addTo(map).bindPopup(`<b>${deliveryData.assignedDrone}</b><br>Flying...`);
    }
}



function connectWebSocket() {
    console.log('🔌 Initializing WebSocket connection...');
    const socket = new SockJS('/ws');
    stompClient = Stomp.over(socket);

    // Enable debug logging for STOMP
    stompClient.debug = function(msg) {
        console.log('STOMP:', msg);
    };

    stompClient.connect({}, function(frame) {
        console.log('✅ WebSocket CONNECTED successfully!', frame);
        console.log('📡 Subscribing to /topic/drone/position...');

        // Subscribe to drone positions
        const subscription = stompClient.subscribe('/topic/drone/position', function(message) {
            console.log('📨 Received WebSocket message:', message.body);
            const position = JSON.parse(message.body);
            console.log('📍 Parsed position:', position);
            updateDronePosition(position);
        });

        console.log('✅ Successfully subscribed to drone position updates!', subscription);
    }, function(error) {
        console.error('❌ WebSocket connection error:', error);
        console.log('🔄 Retrying connection in 3 seconds...');
        setTimeout(connectWebSocket, 3000);
    });
}

function updateDronePosition(position) {
    console.log('🔍 Update check - Received order:', position.orderNumber, 'Current order:', currentOrderNumber, 'State:', orderState);

    // Ignore updates if:
    // 1. Not our order
    // 2. Order is already collected (customer shouldn't see return journey)
    if (position.orderNumber !== currentOrderNumber || orderState === 'COLLECTED') {
        console.log('⚠️ Ignoring update - not our order or already collected');
        return;
    }

    console.log('📍 Drone position update accepted:', position);

    // Create or update drone marker
    if (!droneMarker) {
        droneMarker = L.marker([position.lat, position.lng], {
            icon: L.icon({
                iconUrl: '/images/drone_icon.png',
                iconSize: [40, 35],
                iconAnchor: [20, 17.5],
                popupAnchor: [0, -17.5]
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
        console.log('✅ Drone has arrived! Showing collection button');
        setOrderState('ARRIVED');

        // Show browser notification if permission granted
        if ('Notification' in window && Notification.permission === 'granted') {
            new Notification('MediDrone Delivery 🚁', {
                body: 'Your medication has arrived! Click "Order Collected" when you\'ve retrieved it.',
                icon: '/images/drone_icon.png'
            });
        }

        // Center map on delivery location
        map.panTo([position.lat, position.lng]);
    } else if (orderState === 'IN_TRANSIT') {
        // Pan to follow drone during flight (only if still in transit)
        map.panTo([position.lat, position.lng]);
    }
}

function updateStatusBadge(status) {
    console.log('🏷️ Updating status badge to:', status);

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

    console.log('📦 Confirming pickup for order:', currentOrderNumber);

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
                console.log('✅ Order collected successfully!');
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

