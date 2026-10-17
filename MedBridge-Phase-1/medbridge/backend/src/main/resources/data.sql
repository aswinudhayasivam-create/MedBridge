-- FICTIONAL TEST RECORDS ONLY. NOT verified real-world clinics.
-- One shared BCrypt test password for every account: MedBridgeDemo123!
-- Never load these seeds into a production database.
INSERT IGNORE INTO users (user_id,name,email,password_hash,role,latitude,longitude,verified) VALUES (1,'Admin Demo','admin@medbridge.test','$2a$10$8m2OLfqkB5Txk0qOJZvT7.kA3z2PaQUf43R657hYCyq0vA8PRhv0e','ADMIN',11.0168,76.9558,1);
INSERT IGNORE INTO users (user_id,name,email,password_hash,role,latitude,longitude,verified) VALUES (2,'Donor One','donor1@medbridge.test','$2a$10$8m2OLfqkB5Txk0qOJZvT7.kA3z2PaQUf43R657hYCyq0vA8PRhv0e','DONOR',11.02,76.97,1);
INSERT IGNORE INTO users (user_id,name,email,password_hash,role,latitude,longitude,verified) VALUES (3,'Donor Two','donor2@medbridge.test','$2a$10$8m2OLfqkB5Txk0qOJZvT7.kA3z2PaQUf43R657hYCyq0vA8PRhv0e','DONOR',11.005,76.945,1);
INSERT IGNORE INTO users (user_id,name,email,password_hash,role,latitude,longitude,verified) VALUES (4,'Donor Three','donor3@medbridge.test','$2a$10$8m2OLfqkB5Txk0qOJZvT7.kA3z2PaQUf43R657hYCyq0vA8PRhv0e','DONOR',11.035,76.98,1);
INSERT IGNORE INTO users (user_id,name,email,password_hash,role,latitude,longitude,verified) VALUES (5,'Demo Care North','ngo1@medbridge.test','$2a$10$8m2OLfqkB5Txk0qOJZvT7.kA3z2PaQUf43R657hYCyq0vA8PRhv0e','NGO',11.03,76.962,1);
INSERT IGNORE INTO users (user_id,name,email,password_hash,role,latitude,longitude,verified) VALUES (6,'Demo Health West','ngo2@medbridge.test','$2a$10$8m2OLfqkB5Txk0qOJZvT7.kA3z2PaQUf43R657hYCyq0vA8PRhv0e','NGO',11.01,76.925,1);
INSERT IGNORE INTO users (user_id,name,email,password_hash,role,latitude,longitude,verified) VALUES (7,'Demo Relief South','ngo3@medbridge.test','$2a$10$8m2OLfqkB5Txk0qOJZvT7.kA3z2PaQUf43R657hYCyq0vA8PRhv0e','NGO',10.98,76.955,1);
INSERT IGNORE INTO users (user_id,name,email,password_hash,role,latitude,longitude,verified) VALUES (8,'Unverified Demo NGO','ngo4@medbridge.test','$2a$10$8m2OLfqkB5Txk0qOJZvT7.kA3z2PaQUf43R657hYCyq0vA8PRhv0e','NGO',11.05,77.01,0);
INSERT IGNORE INTO ngo_profiles (ngo_id,org_name,license_no,service_area) VALUES (5,'Demo Care North','DEMO-LIC-001','Coimbatore North');
INSERT IGNORE INTO ngo_profiles (ngo_id,org_name,license_no,service_area) VALUES (6,'Demo Health West','DEMO-LIC-002','Coimbatore West');
INSERT IGNORE INTO ngo_profiles (ngo_id,org_name,license_no,service_area) VALUES (7,'Demo Relief South','DEMO-LIC-003','Coimbatore South');
INSERT IGNORE INTO ngo_profiles (ngo_id,org_name,license_no,service_area) VALUES (8,'Unverified Demo NGO','DEMO-LIC-004','Coimbatore East');
INSERT IGNORE INTO medicines (medicine_id,donor_id,name,batch_no,expiry_date,quantity,category,status,image_url,estimated_value) VALUES (1,2,'Paracetamol 500 mg','MB-1001','2027-12-31',12,'Pain Relief','MATCHED',NULL,24.00);
INSERT IGNORE INTO medicines (medicine_id,donor_id,name,batch_no,expiry_date,quantity,category,status,image_url,estimated_value) VALUES (2,3,'Cetirizine 10 mg','MB-1002','2027-06-30',10,'Allergy','APPROVED',NULL,30.00);
INSERT IGNORE INTO medicines (medicine_id,donor_id,name,batch_no,expiry_date,quantity,category,status,image_url,estimated_value) VALUES (3,4,'ORS Sachet','MB-1003','2027-11-30',6,'Hydration','MATCHED',NULL,60.00);
INSERT IGNORE INTO medicines (medicine_id,donor_id,name,batch_no,expiry_date,quantity,category,status,image_url,estimated_value) VALUES (4,2,'Vitamin C 500 mg','MB-1004','2028-01-31',15,'Supplements','REQUESTED',NULL,75.00);
INSERT IGNORE INTO medicines (medicine_id,donor_id,name,batch_no,expiry_date,quantity,category,status,image_url,estimated_value) VALUES (5,3,'Calcium Tablets','MB-1005','2027-09-30',20,'Supplements','COMPLETED',NULL,120.00);
INSERT IGNORE INTO medicines (medicine_id,donor_id,name,batch_no,expiry_date,quantity,category,status,image_url,estimated_value) VALUES (6,4,'Antacid Tablets','MB-1006','2027-08-31',10,'Digestive Care','APPROVED',NULL,35.00);
INSERT IGNORE INTO medicines (medicine_id,donor_id,name,batch_no,expiry_date,quantity,category,status,image_url,estimated_value) VALUES (7,2,'Cough Lozenges','MB-1007','2027-04-30',12,'Throat Care','LISTED',NULL,36.00);
INSERT IGNORE INTO medicines (medicine_id,donor_id,name,batch_no,expiry_date,quantity,category,status,image_url,estimated_value) VALUES (8,3,'Paracetamol 500 mg','MB-1008','2026-10-25',8,'Pain Relief','FLAGGED',NULL,16.00);
INSERT IGNORE INTO medicines (medicine_id,donor_id,name,batch_no,expiry_date,quantity,category,status,image_url,estimated_value) VALUES (9,4,'Saline Nasal Spray','MB-1009','2027-07-31',2,'Respiratory Care','APPROVED',NULL,90.00);
INSERT IGNORE INTO medicines (medicine_id,donor_id,name,batch_no,expiry_date,quantity,category,status,image_url,estimated_value) VALUES (10,2,'Multivitamin Tablets','MB-1010','2027-05-31',30,'Supplements','MATCHED',NULL,150.00);
INSERT IGNORE INTO requests (request_id,medicine_id,receiver_id,status,requested_at,approved_at) VALUES
 (1,4,5,'PENDING','2026-10-16 11:00:00',NULL),
 (2,5,6,'COLLECTED','2026-10-10 11:00:00','2026-10-11 11:00:00');
INSERT IGNORE INTO notifications (notif_id,user_id,type,message,`read`) VALUES
 (1,2,'LISTING_FLAGGED','One demo listing needs an administrator review.',FALSE),
 (2,2,'NEW_REQUEST','A demo NGO requested your listed medicine.',FALSE),
 (3,5,'NEW_MATCH','A medicine match may be available in your area.',FALSE);
