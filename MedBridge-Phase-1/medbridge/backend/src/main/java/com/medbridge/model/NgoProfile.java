package com.medbridge.model;

import jakarta.persistence.*;

@Entity
@Table(name = "ngo_profiles")
public class NgoProfile {
    @Id @Column(name = "ngo_id") private Long id;
    @MapsId @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "ngo_id") private User user;
    @Column(name = "org_name", nullable = false, length = 200) private String orgName;
    @Column(name = "license_no", nullable = false, unique = true, length = 100) private String licenseNo;
    @Column(name = "service_area", nullable = false, length = 200) private String serviceArea;
    public Long getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User v) { user=v; }
    public String getOrgName() { return orgName; }
    public void setOrgName(String v) { orgName=v; }
    public String getLicenseNo() { return licenseNo; }
    public void setLicenseNo(String v) { licenseNo=v; }
    public String getServiceArea() { return serviceArea; }
    public void setServiceArea(String v) { serviceArea=v; }
}
