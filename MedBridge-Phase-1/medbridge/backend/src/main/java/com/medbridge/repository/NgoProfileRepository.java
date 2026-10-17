package com.medbridge.repository;
import com.medbridge.model.NgoProfile;
import org.springframework.data.jpa.repository.JpaRepository;
public interface NgoProfileRepository extends JpaRepository<NgoProfile, Long> {
    boolean existsByLicenseNoIgnoreCase(String licenseNo);
}
