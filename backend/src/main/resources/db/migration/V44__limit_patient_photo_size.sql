ALTER TABLE patient_profile_photo DROP CONSTRAINT patient_profile_photo_image_data_check;
ALTER TABLE patient_profile_photo
  ADD CONSTRAINT patient_profile_photo_size_check CHECK (octet_length(image_data) <= 102400) NOT VALID;
