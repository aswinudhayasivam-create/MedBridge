package com.medbridge.review;
import com.medbridge.demo.DemoService;

import com.fasterxml.jackson.databind.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"app.demo-workflows=false","app.allow-registration=false"})
@ActiveProfiles("review")
@org.testcontainers.junit.jupiter.Testcontainers
@org.springframework.context.annotation.Import(ReviewMySqlIT.TestTime.class)
class ReviewMySqlIT {
    static String secret(){return UUID.randomUUID().toString()+UUID.randomUUID();}
    static final String DATABASE_PASSWORD=secret(), GATE=secret(), SIGNING=secret();
    static final Map<String,String> PASSWORDS=Map.of("donor",secret(),"ngo",secret(),"pending",secret(),"admin",secret());
    @org.testcontainers.junit.jupiter.Container
    static final org.testcontainers.containers.MySQLContainer<?> MYSQL=new org.testcontainers.containers.MySQLContainer<>("mysql:8.4")
        .withDatabaseName(ReviewModePolicy.DATABASE).withUsername("review_schema_owner").withPassword(secret())
        .withCreateContainerCmdModifier(cmd->cmd.getHostConfig().withPortBindings(new com.github.dockerjava.api.model.PortBinding(
            com.github.dockerjava.api.model.Ports.Binding.bindIpAndPort("127.0.0.1",13307),new com.github.dockerjava.api.model.ExposedPort(3306))));
    static final java.util.concurrent.atomic.AtomicReference<Instant> NOW=new java.util.concurrent.atomic.AtomicReference<>(Instant.parse("2026-10-25T06:00:00Z"));
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods=false)
    static class TestTime {
        @org.springframework.context.annotation.Bean @org.springframework.context.annotation.Primary
        ReviewModePolicy fixedReviewPolicy(org.springframework.core.env.Environment env){
            return new ReviewModePolicy(env,new Clock(){public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}public Instant instant(){return NOW.get();}});
        }
    }
    @org.junit.jupiter.api.BeforeEach void newRateWindow(){NOW.updateAndGet(time -> time.plusSeconds(60));}
    @org.springframework.test.context.DynamicPropertySource
    static void properties(org.springframework.test.context.DynamicPropertyRegistry registry){
        var owner=new org.springframework.jdbc.datasource.DriverManagerDataSource(MYSQL.getJdbcUrl(),"root",MYSQL.getPassword());
        var initializer=new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator();
        for(String resource:List.of("schema.sql","demo-schema.sql","review-schema.sql"))initializer.addScript(new org.springframework.core.io.ClassPathResource(resource));
        initializer.execute(owner);
        var jdbc=new JdbcTemplate(owner);
        jdbc.execute("CREATE USER IF NOT EXISTS 'mb_review_fixture'@'%' IDENTIFIED BY '"+DATABASE_PASSWORD+"'");
        jdbc.execute("GRANT SELECT,INSERT,UPDATE,DELETE ON "+ReviewModePolicy.DATABASE+".* TO 'mb_review_fixture'@'%'");
        Map<String,String> values=new HashMap<>(Map.ofEntries(
            Map.entry("app.review.enabled","true"),Map.entry("app.review.purpose",ReviewModePolicy.PURPOSE),Map.entry("app.review.transport","loopback-test"),
            Map.entry("app.review.expires-at",ReviewModePolicy.HARD_STOP.toString()),Map.entry("app.review.access-key",GATE),
            Map.entry("server.address","127.0.0.1"),Map.entry("app.frontend-url","http://127.0.0.1:5174"),
            Map.entry("spring.datasource.url",ReviewModePolicy.LOCAL_URL),Map.entry("spring.datasource.username","mb_review_fixture"),
            Map.entry("spring.datasource.password",DATABASE_PASSWORD),Map.entry("spring.datasource.driver-class-name","com.mysql.cj.jdbc.Driver"),
            Map.entry("spring.sql.init.mode","never"),Map.entry("spring.jpa.hibernate.ddl-auto","none"),Map.entry("app.jwt.secret",SIGNING)));
        PASSWORDS.forEach((key,value)->values.put("app.review."+key+"-password",value));
        values.forEach((key,value)->registry.add(key,()->value));
    }

    @Autowired TestRestTemplate http; @Autowired ObjectMapper json; @Autowired JdbcTemplate db;
    @Autowired PasswordEncoder passwordEncoder;
    @LocalServerPort int port;
    String donor,ngo,pending,admin;final List<Long> medicineIds=new ArrayList<>();
    ResponseEntity<String> call(HttpMethod method,String path,Object body,String token){
        var headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);headers.set("X-MedBridge-Review-Key",GATE);if(token!=null)headers.setBearerAuth(token);
        return http.exchange("http://127.0.0.1:"+port+path,method,new HttpEntity<>(body,headers),String.class);
    }
    JsonNode data(ResponseEntity<String> response)throws Exception{return json.readTree(response.getBody());}
    String login(String email,String password)throws Exception{var response=call(HttpMethod.POST,"/api/auth/login",Map.of("email",email,"password",password),null);assertEquals(200,response.getStatusCode().value());return data(response).path("token").asText();}
    long photo()throws Exception{
        var bytes=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(120,60,BufferedImage.TYPE_INT_RGB),"png",bytes);
        var body=new LinkedMultiValueMap<String,Object>();body.add("file",new ByteArrayResource(bytes.toByteArray()){@Override public String getFilename(){return "synthetic.png";}});
        var headers=new HttpHeaders();headers.setBearerAuth(donor);headers.setContentType(MediaType.MULTIPART_FORM_DATA);headers.set("X-MedBridge-Review-Key",GATE);
        var response=http.exchange("http://127.0.0.1:"+port+"/api/demo/photos",HttpMethod.POST,new HttpEntity<>(body,headers),String.class);
        assertEquals(201,response.getStatusCode().value());assertFalse(data(response).path("ocrPerformed").asBoolean());return data(response).path("photoId").asLong();
    }
    Map<String,Object> medicine(long photo,LocalDate expiry){return Map.ofEntries(Map.entry("name","Synthetic IT "+UUID.randomUUID()),Map.entry("form","Tablets"),Map.entry("category","General"),Map.entry("quantity",4),Map.entry("unit","strips"),Map.entry("expiry",expiry.toString()),Map.entry("batch","SYNTHETIC"),Map.entry("storage","Fictional cool dry storage"),Map.entry("sealed",true),Map.entry("declared",true),Map.entry("photoConfirmed",true),Map.entry("photoId",photo));}
    long submit(long photo)throws Exception{var r=call(HttpMethod.POST,"/api/demo/medicines",medicine(photo,LocalDate.now().plusMonths(8)),donor);assertEquals(201,r.getStatusCode().value(),r.getBody());long id=data(r).path("medicineId").asLong();medicineIds.add(id);return id;}
    long request(long id,String token)throws Exception{var r=call(HttpMethod.POST,"/api/demo/medicines/"+id+"/requests",Map.of("quantity",3,"note","Fictional demo request"),token);assertEquals(201,r.getStatusCode().value(),r.getBody());return data(r).path("requestId").asLong();}
    @Test void completePersistedJourneyAndSecurityBoundaries()throws Exception{
        donor=login("donor@review.medbridge.invalid",PASSWORDS.get("donor"));ngo=login("ngo@review.medbridge.invalid",PASSWORDS.get("ngo"));pending=login("pending@review.medbridge.invalid",PASSWORDS.get("pending"));admin=login("admin@review.medbridge.invalid",PASSWORDS.get("admin"));
        long pendingId=db.queryForObject("SELECT user_id FROM users WHERE email='pending@review.medbridge.invalid'",Long.class);
        try{
            assertEquals(403,call(HttpMethod.GET,"/api/demo/browse",null,pending).getStatusCode().value());
            assertEquals(503,call(HttpMethod.POST,"/api/auth/register",Map.of("name","Fictional","email","blocked@example.invalid","password",UUID.randomUUID().toString(),"role","DONOR"),null).getStatusCode().value());
            long photo=photo();
            assertEquals(400,call(HttpMethod.POST,"/api/demo/medicines",medicine(photo,LocalDate.now().minusDays(1)),donor).getStatusCode().value());
            assertEquals(400,call(HttpMethod.POST,"/api/demo/medicines",medicine(photo,LocalDate.now().plusMonths(2)),donor).getStatusCode().value());
            long id=submit(photo);
            assertFalse(call(HttpMethod.GET,"/api/demo/browse",null,ngo).getBody().contains("\"medicine_id\":"+id+","));
            assertEquals(403,call(HttpMethod.POST,"/api/demo/medicines/"+id+"/review",Map.of("approve",true,"note","Not an admin"),donor).getStatusCode().value());
            assertEquals(200,call(HttpMethod.POST,"/api/demo/medicines/"+id+"/review",Map.of("approve",true,"note","Synthetic IT review only"),admin).getStatusCode().value());
            var browsing=call(HttpMethod.GET,"/api/demo/browse",null,ngo);assertEquals(200,browsing.getStatusCode().value(),browsing.getBody());String browse=browsing.getBody();assertTrue(browse.contains("\"medicine_id\":"+id),browse);assertFalse(browse.contains("latitude"));assertFalse(browse.contains("longitude"));
            assertEquals(400,call(HttpMethod.POST,"/api/demo/medicines/"+id+"/requests",Map.of("quantity",5,"note","Too many"),ngo).getStatusCode().value());
            long request=request(id,ngo);
            assertEquals(409,call(HttpMethod.POST,"/api/demo/medicines/"+id+"/requests",Map.of("quantity",1,"note","Duplicate"),ngo).getStatusCode().value());
            assertEquals(403,call(HttpMethod.POST,"/api/demo/requests/"+request+"/decision",Map.of("approve",true,"note","Wrong owner role"),ngo).getStatusCode().value());
            assertEquals(200,call(HttpMethod.POST,"/api/demo/requests/"+request+"/decision",Map.of("approve",true,"note","Accepted fictional request"),donor).getStatusCode().value());
            var schedule=Map.of("when",LocalDateTime.now().plusHours(1).withNano(0).toString(),"note","Synthetic pickup zone; no real address");
            assertEquals(200,call(HttpMethod.POST,"/api/demo/requests/"+request+"/schedule",schedule,donor).getStatusCode().value());
            assertEquals(403,call(HttpMethod.GET,"/api/demo/requests/"+request+"/code",null,ngo).getStatusCode().value());
            assertFalse(call(HttpMethod.GET,"/api/demo/requests",null,ngo).getBody().contains("code_hash"));
            String code=data(call(HttpMethod.GET,"/api/demo/requests/"+request+"/code",null,donor)).path("code").asText();assertTrue(code.matches("[0-9]{4}"));
            var protectedCode=db.queryForMap("SELECT code_hash,code_cipher FROM demo_request_details WHERE request_id=?",request);assertNotEquals(code,protectedCode.get("code_hash"));assertNotEquals(code,protectedCode.get("code_cipher"));
            String wrong=code.equals("0000")?"1111":"0000";
            for(int i=0;i<5;i++)assertEquals(400,call(HttpMethod.POST,"/api/demo/requests/"+request+"/collect",Map.of("code",wrong),ngo).getStatusCode().value());
            assertEquals(5,db.queryForObject("SELECT code_attempts FROM demo_request_details WHERE request_id=?",Integer.class,request));
            assertEquals(400,call(HttpMethod.POST,"/api/demo/requests/"+request+"/collect",Map.of("code",code),ngo).getStatusCode().value());
            call(HttpMethod.POST,"/api/demo/requests/"+request+"/schedule",schedule,donor);
            db.update("UPDATE demo_request_details SET code_expires=? WHERE request_id=?",LocalDateTime.now().minusHours(1),request);
            assertEquals(400,call(HttpMethod.POST,"/api/demo/requests/"+request+"/collect",Map.of("code",code),ngo).getStatusCode().value());
            call(HttpMethod.POST,"/api/demo/requests/"+request+"/schedule",schedule,donor);
            code=data(call(HttpMethod.GET,"/api/demo/requests/"+request+"/code",null,donor)).path("code").asText();
            assertEquals(403,call(HttpMethod.POST,"/api/demo/requests/"+request+"/collect",Map.of("code",code),pending).getStatusCode().value());
            assertEquals(200,call(HttpMethod.POST,"/api/demo/requests/"+request+"/collect",Map.of("code",code),ngo).getStatusCode().value());
            assertEquals("COLLECTED",db.queryForObject("SELECT status FROM requests WHERE request_id=?",String.class,request));
            assertEquals(400,call(HttpMethod.POST,"/api/demo/requests/"+request+"/collect",Map.of("code",code),ngo).getStatusCode().value());
            assertTrue(call(HttpMethod.GET,"/api/demo/notifications",null,donor).getBody().contains("Simulated collection completed"));

            // Two real concurrent acceptances reserve against the same locked listing.
            long second=submit(photo);call(HttpMethod.POST,"/api/demo/medicines/"+second+"/review",Map.of("approve",true,"note","Concurrency fixture"),admin);
            call(HttpMethod.POST,"/api/demo/ngos/"+pendingId+"/review",Map.of("approve",true,"note","Temporary fictional integration verification"),admin);
            long a=request(second,ngo),b=request(second,pending);
            var pool=Executors.newFixedThreadPool(2);
            try{
                var results=pool.invokeAll(List.of(()->call(HttpMethod.POST,"/api/demo/requests/"+a+"/decision",Map.of("approve",true,"note","Concurrent A"),donor).getStatusCode().value(),()->call(HttpMethod.POST,"/api/demo/requests/"+b+"/decision",Map.of("approve",true,"note","Concurrent B"),donor).getStatusCode().value()));
                Set<Object> statuses=new HashSet<>();for(var result:results)statuses.add(result.get());assertEquals(Set.of(200,409),statuses);
                assertEquals(3,db.queryForObject("SELECT reserved FROM demo_medicine_details WHERE medicine_id=?",Integer.class,second));
            }finally{pool.shutdownNow();}
        }finally{
            db.update("UPDATE users SET verified=false WHERE user_id=?",pendingId);db.update("UPDATE demo_ngo_applications SET status='PENDING' WHERE ngo_id=?",pendingId);
            for(long id:medicineIds){db.update("DELETE FROM demo_events WHERE medicine_id=?",id);db.update("DELETE FROM requests WHERE medicine_id=?",id);db.update("DELETE FROM medicines WHERE medicine_id=?",id);}
        }
    }
    @Test void haversineRadiusIsGeographicAndStable(){assertEquals(0,DemoService.distance(13,80,13,80),0.0001);assertTrue(DemoService.distance(13,80,13.2,80)>10);}
    @Test void anotherDonorCannotReadPrivatePhotoOrChangeOwnership()throws Exception{
        donor=login("donor@review.medbridge.invalid",PASSWORDS.get("donor"));
        String email="owner-test-"+UUID.randomUUID()+"@review.medbridge.invalid",password=UUID.randomUUID().toString();
        db.update("INSERT INTO users(name,email,password_hash,role,verified) VALUES(?,?,?,'DONOR',false)","Fictional ownership test",email,passwordEncoder.encode(password));
        long other=db.queryForObject("SELECT user_id FROM users WHERE email=?",Long.class,email);long photo=photo();long medicine=submit(photo);
        try{
            String token=login(email,password);
            assertEquals(403,call(HttpMethod.GET,"/api/demo/medicines/"+medicine+"/events",null,token).getStatusCode().value());
            assertEquals(403,call(HttpMethod.GET,"/api/demo/photos/"+photo,null,token).getStatusCode().value());
            assertEquals(403,call(HttpMethod.POST,"/api/demo/medicines/"+medicine+"/review",Map.of("approve",true,"note","Different donor cannot review"),token).getStatusCode().value());
            assertEquals(401,call(HttpMethod.GET,"/api/demo/summary",null,null).getStatusCode().value());
        }finally{
            db.update("DELETE FROM demo_events WHERE medicine_id=?",medicine);db.update("DELETE FROM medicines WHERE medicine_id=?",medicine);medicineIds.remove(medicine);
            db.update("DELETE FROM demo_photos WHERE photo_id=?",photo);db.update("DELETE FROM users WHERE user_id=?",other);
        }
    }

    @Test void reviewGateRegistrationAndOriginalLocalIdentitiesFailClosed()throws Exception{
        assertEquals(401,http.getForEntity("http://127.0.0.1:"+port+"/api/auth/review-mode",String.class).getStatusCode().value());
        for(String email:List.of("meera@medbridge.test","divya@medbridge.test","pending@medbridge.test","admin@medbridge.test"))
            assertEquals(401,call(HttpMethod.POST,"/api/auth/login",Map.of("email",email,"password",secret()),null).getStatusCode().value());
        assertEquals(503,call(HttpMethod.POST,"/api/auth/register",Map.of(),null).getStatusCode().value());
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM users WHERE email LIKE '%@medbridge.test'",Integer.class));
    }
    @Test void alreadyIssuedReviewJwtExpiresAtTheServerCutoff()throws Exception{
        String token=login("donor@review.medbridge.invalid",PASSWORDS.get("donor"));Instant prior=NOW.get();
        try{NOW.set(ReviewModePolicy.HARD_STOP);assertEquals(503,call(HttpMethod.GET,"/api/demo/summary",null,token).getStatusCode().value());
            assertEquals(503,http.getForEntity("http://127.0.0.1:"+port+"/api/review/ready",String.class).getStatusCode().value());
        }finally{NOW.set(prior);}
    }
}
