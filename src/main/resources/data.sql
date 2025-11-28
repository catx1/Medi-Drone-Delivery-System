-- Insert sample medications
INSERT INTO medications (name, description, requires_refrigeration, weight_grams, stock_quantity)
VALUES
('Paracetamol 500mg', 'Pain relief and fever reduction', FALSE, 50, 100),
('Ibuprofen 400mg', 'Anti-inflammatory pain relief', FALSE, 60, 80),
('Insulin (Humalog)', 'Fast-acting insulin for diabetes', TRUE, 30, 50),
('Amoxicillin 500mg', 'Antibiotic for bacterial infections', FALSE, 70, 60),
('Salbutamol Inhaler', 'Asthma relief inhaler', FALSE, 45, 40),
('Aspirin 75mg', 'Blood thinner for heart health', FALSE, 40, 90),
('Omeprazole 20mg', 'Reduces stomach acid', FALSE, 55, 70);