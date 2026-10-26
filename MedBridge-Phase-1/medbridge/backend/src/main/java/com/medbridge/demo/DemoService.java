package com.medbridge.demo;

import com.medbridge.exception.ApiException;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import javax.imageio.ImageIO;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DemoService {
    private final JdbcTemplate db; private final DemoSafety safety; private final PasswordEncoder passwords;
    private final Environment environment; private final SecureRandom random=new SecureRandom();
    public DemoService(JdbcTemplate db,DemoSafety safety,PasswordEncoder passwords,Environment environment){
        this.db=db;this.safety=safety;this.passwords=passwords;this.environment=environment;
    }
    private ApiException bad(String message){return new ApiException(HttpStatus.BAD_REQUEST,message);}
    private ApiException denied(){return new ApiException(HttpStatus.FORBIDDEN,"This record belongs to another account.");}
    private Map<String,Object> one(String sql,Object...args){var rows=db.queryForList(sql,args);if(rows.size()!=1)throw new ApiException(HttpStatus.NOT_FOUND,"Demo record not found.");return rows.get(0);}
    private long uid(Map<String,Object> user){return DemoSafety.id(user,"user_id");}
    private long number(Map<String,Object> row,String key){return DemoSafety.id(row,key);}
    private LocalDateTime time(Object value){return value instanceof LocalDateTime t?t:((java.sql.Timestamp)value).toLocalDateTime();}
    private void event(long actor,long recipient,Long medicine,Long request,String type,String message){
        db.update("INSERT INTO demo_events(actor_id,recipient_id,medicine_id,request_id,event_type,message) VALUES(?,?,?,?,?,?)",actor,recipient,medicine,request,type,message);
        db.update("INSERT INTO notifications(user_id,type,message) VALUES(?,?,?)",recipient,type,message);
    }
    private void admins(long actor,Long medicine,String type,String message){
        for(var admin:db.queryForList("SELECT user_id FROM users WHERE role='ADMIN'"))event(actor,number(admin,"user_id"),medicine,null,type,message);
    }
    public Map<String,Object> summary(String email){
        var actor=safety.actor(email,"DONOR","NGO","ADMIN"); long id=uid(actor);String role=(String)actor.get("role");
        var result=new LinkedHashMap<String,Object>();
        result.put("user",one("SELECT u.user_id,u.name,u.role,u.verified,n.org_name,n.license_no FROM users u LEFT JOIN ngo_profiles n ON n.ngo_id=u.user_id WHERE u.user_id=?",id));
        result.put("unread",db.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id=? AND sent_at IS NOT NULL AND `read`=false",Long.class,id));
        if(role.equals("DONOR")){
            result.put("medicines",medicines(email));result.put("requests",requests(email));
        }else if(role.equals("NGO")){
            result.put("requests",requests(email));
            result.put("eligible",Boolean.TRUE.equals(actor.get("verified"))?browse(email,10,0,"").size():0);
            result.put("application",db.queryForList("SELECT status,applied_at,reviewed_at FROM demo_ngo_applications WHERE ngo_id=?",id));
        }else{
            result.put("medicines",medicines(email));result.put("requests",requests(email));
            result.put("accounts",db.queryForList("SELECT u.user_id,u.name,u.email,u.role,u.verified,n.org_name,n.license_no,a.status AS application_status FROM users u LEFT JOIN ngo_profiles n ON n.ngo_id=u.user_id LEFT JOIN demo_ngo_applications a ON a.ngo_id=u.user_id"));
            result.put("audit",db.queryForList("SELECT e.event_id,e.event_type,e.message,e.created_at,u.name AS actor FROM demo_events e JOIN users u ON u.user_id=e.actor_id ORDER BY e.event_id DESC LIMIT 100"));
        }
        return result;
    }
    private static final String MEDICINES="SELECT m.medicine_id,m.name,m.batch_no,m.expiry_date,m.quantity,m.category,m.status,m.created_at,m.donor_id,u.name AS donor_name,d.unit,d.medicine_form,d.reserved,d.storage_note,d.sealed,d.declared,d.photo_confirmed,d.photo_id,d.review_note,d.reviewed_at FROM medicines m JOIN demo_medicine_details d ON d.medicine_id=m.medicine_id JOIN users u ON u.user_id=m.donor_id ";
    public List<Map<String,Object>> medicines(String email){var u=safety.actor(email,"DONOR","ADMIN");return "ADMIN".equals(u.get("role"))?db.queryForList(MEDICINES+"ORDER BY m.medicine_id DESC"):db.queryForList(MEDICINES+"WHERE m.donor_id=? ORDER BY m.medicine_id DESC",uid(u));}
    public List<Map<String,Object>> medicineEvents(String email,long id){
        var u=safety.actor(email,"DONOR","NGO","ADMIN");var m=one("SELECT donor_id FROM medicines WHERE medicine_id=?",id);
        if(!"ADMIN".equals(u.get("role"))&&number(m,"donor_id")!=uid(u))throw denied();
        return db.queryForList("SELECT event_id,event_type,message,created_at FROM demo_events WHERE medicine_id=? AND recipient_id=? ORDER BY event_id",id,uid(u));
    }
    @Transactional public Map<String,Object> submit(String email,DemoController.MedicineInput input){
        var u=safety.actor(email,"DONOR");
        if(input.expiry().isBefore(LocalDate.now().plusMonths(3)))throw bad("This fictional demo requires at least three months of remaining shelf life.");
        if(!input.sealed()||!input.declared()||!input.photoConfirmed())throw bad("Sealed pack, synthetic-only declaration and label confirmation are required.");
        one("SELECT photo_id FROM demo_photos WHERE photo_id=? AND donor_id=?",input.photoId(),uid(u));
        db.update("INSERT INTO medicines(donor_id,name,batch_no,expiry_date,quantity,category,status) VALUES(?,?,?,?,?,?,'LISTED')",uid(u),input.name().trim(),input.batch().trim(),input.expiry(),input.quantity(),input.category());
        long id=db.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        db.update("INSERT INTO demo_medicine_details(medicine_id,unit,medicine_form,storage_note,sealed,declared,photo_confirmed,photo_id) VALUES(?,?,?,?,true,true,true,?)",id,input.unit(),input.form(),input.storage(),input.photoId());
        event(uid(u),uid(u),id,null,"SUBMITTED","Submission received for fictional admin review: "+input.name());
        admins(uid(u),id,"REVIEW_REQUIRED","A fictional medicine awaits review.");
        return Map.of("medicineId",id,"status","Submitted for review");
    }
    @Transactional public Map<String,Object> upload(String email,MultipartFile file){
        var u=safety.actor(email,"DONOR");
        if(file.isEmpty()||file.getSize()>1_000_000)throw bad("Use a synthetic PNG or JPEG label under 1 MB.");
        try(var stream=ImageIO.createImageInputStream(file.getInputStream())){
            var readers=ImageIO.getImageReaders(stream);if(!readers.hasNext())throw bad("Invalid image; use PNG or JPEG.");
            var reader=readers.next();
            try{
                reader.setInput(stream);String format=reader.getFormatName();
                if(!Set.of("png","jpeg","jpg").contains(format.toLowerCase(Locale.ROOT)))throw bad("Only PNG or JPEG images are supported.");
                int w=reader.getWidth(0),h=reader.getHeight(0);if(w<1||h<1||w>2400||h>2400)throw bad("Image dimensions must be at most 2400 pixels.");
                var image=reader.read(0);var bytes=new ByteArrayOutputStream();ImageIO.write(image,"png",bytes);
                if(bytes.size()>2_000_000)throw bad("Decoded image is too large.");
                db.update("INSERT INTO demo_photos(donor_id,image_bytes,mime) VALUES(?,?,'image/png')",uid(u),bytes.toByteArray());
                return Map.of("photoId",db.queryForObject("SELECT LAST_INSERT_ID()",Long.class),"ocrPerformed",false,"message","OCR is unavailable. Manually confirm the synthetic label details.");
            }finally{reader.dispose();}
        }catch(IOException ex){throw bad("The synthetic image could not be read.");}
    }
    public byte[] photo(String email,long id){
        var u=safety.actor(email,"DONOR","NGO","ADMIN");var photo=one("SELECT donor_id,image_bytes FROM demo_photos WHERE photo_id=?",id);
        if(!"ADMIN".equals(u.get("role"))&&number(photo,"donor_id")!=uid(u)){
            safety.verifiedNgo(email);
            if(db.queryForObject("SELECT COUNT(*) FROM demo_medicine_details d JOIN medicines m ON m.medicine_id=d.medicine_id WHERE d.photo_id=? AND m.status='APPROVED' AND m.expiry_date>=CURRENT_DATE",Integer.class,id)==0)throw denied();
        }
        return (byte[])photo.get("image_bytes");
    }
    @Transactional public void review(String email,long id,DemoController.Decision decision){
        var u=safety.actor(email,"ADMIN");var m=one("SELECT donor_id,status,expiry_date FROM medicines WHERE medicine_id=? FOR UPDATE",id);
        one("SELECT medicine_id FROM demo_medicine_details WHERE medicine_id=?",id);
        if(!"LISTED".equals(m.get("status")))throw bad("This submission has already been reviewed.");
        if(decision.approve()&&((java.sql.Date)m.get("expiry_date")).toLocalDate().isBefore(LocalDate.now().plusMonths(3)))throw bad("Shelf life is no longer sufficient for approval.");
        String status=decision.approve()?"APPROVED":"REJECTED";
        db.update("UPDATE medicines SET status=? WHERE medicine_id=?",status,id);
        db.update("UPDATE demo_medicine_details SET review_note=?,reviewed_at=CURRENT_TIMESTAMP WHERE medicine_id=?",decision.note(),id);
        event(uid(u),number(m,"donor_id"),id,null,status,"Fictional admin decision: "+status+". "+decision.note());
        if(decision.approve())for(var ngo:db.queryForList("SELECT user_id,email FROM users WHERE role='NGO' AND verified=true")){
            boolean nearby=browse((String)ngo.get("email"),10,0,"").stream().anyMatch(row->number(row,"medicine_id")==id);
            if(nearby)event(uid(u),number(ngo,"user_id"),id,null,"NEARBY_LISTING","A newly approved fictional listing is available within 10 km.");
        }
    }
    public static double distance(double lat1,double lon1,double lat2,double lon2){
        double a=Math.pow(Math.sin(Math.toRadians(lat2-lat1)/2),2)+Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))*Math.pow(Math.sin(Math.toRadians(lon2-lon1)/2),2);
        return 6371*2*Math.atan2(Math.sqrt(a),Math.sqrt(Math.max(0,1-a)));
    }
    public List<Map<String,Object>> browse(String email,double radius,int months,String category){
        var u=safety.verifiedNgo(email);if(radius<=0||radius>10||months<0||months>24)throw bad("Invalid discovery filters.");
        List<Map<String,Object>> result=new ArrayList<>();
        if(u.get("latitude")==null||u.get("longitude")==null)return result;
        for(var m:db.queryForList(MEDICINES+"WHERE m.status='APPROVED' AND m.expiry_date>=? AND m.quantity>d.reserved ORDER BY m.expiry_date",LocalDate.now().plusMonths(months).plusDays(months>0?1:0))){
            var location=one("SELECT latitude,longitude FROM users WHERE user_id=?",m.get("donor_id"));
            if(location.get("latitude")==null||location.get("longitude")==null)continue;
            double km=distance(((Number)u.get("latitude")).doubleValue(),((Number)u.get("longitude")).doubleValue(),((Number)location.get("latitude")).doubleValue(),((Number)location.get("longitude")).doubleValue());
            if(km<=radius&&(category.isBlank()||category.equals(m.get("category")))){
                var card=new LinkedHashMap<>(m);card.remove("donor_id");card.remove("storage_note");card.remove("review_note");
                card.put("distance",Math.round(km*10)/10.0);card.put("available",number(m,"quantity")-number(m,"reserved"));result.add(card);
            }
        }
        return result;
    }
    private static final String REQUESTS="SELECT r.request_id,r.medicine_id,r.receiver_id,r.status,r.requested_at,r.approved_at,m.name AS medicine_name,m.donor_id,u.name AS donor_name,n.org_name,n.license_no,v.verified,d.quantity,d.note,d.scheduled_for,d.collection_note,d.scheduled_at,d.completed_at,d.code_expires,d.code_attempts FROM requests r JOIN demo_request_details d ON d.request_id=r.request_id JOIN medicines m ON m.medicine_id=r.medicine_id JOIN users u ON u.user_id=m.donor_id JOIN users v ON v.user_id=r.receiver_id JOIN ngo_profiles n ON n.ngo_id=r.receiver_id ";
    public List<Map<String,Object>> requests(String email){
        var u=safety.actor(email,"DONOR","NGO","ADMIN");String role=(String)u.get("role");
        if(role.equals("ADMIN"))return db.queryForList(REQUESTS+"ORDER BY r.request_id DESC");
        return db.queryForList(REQUESTS+"WHERE "+(role.equals("DONOR")?"m.donor_id":"r.receiver_id")+"=? ORDER BY r.request_id DESC",uid(u));
    }
    @Transactional public Map<String,Object> request(String email,long medicine,DemoController.RequestInput input){
        var u=safety.verifiedNgo(email);var m=one("SELECT quantity,status,expiry_date,donor_id FROM medicines WHERE medicine_id=? FOR UPDATE",medicine);
        var details=one("SELECT reserved FROM demo_medicine_details WHERE medicine_id=?",medicine);
        if(!"APPROVED".equals(m.get("status"))||((java.sql.Date)m.get("expiry_date")).toLocalDate().isBefore(LocalDate.now()))throw bad("Only approved, unexpired listings can be requested.");
        if(input.quantity()>number(m,"quantity")-number(details,"reserved"))throw bad("Requested quantity exceeds availability.");
        if(browse(email,10,0,"").stream().noneMatch(row->number(row,"medicine_id")==medicine))throw denied();
        if(db.queryForObject("SELECT COUNT(*) FROM requests WHERE medicine_id=? AND receiver_id=?",Integer.class,medicine,uid(u))>0)throw new ApiException(HttpStatus.CONFLICT,"A request already exists for this listing.");
        db.update("INSERT INTO requests(medicine_id,receiver_id,status) VALUES(?,?,'PENDING')",medicine,uid(u));long id=db.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        db.update("INSERT INTO demo_request_details(request_id,quantity,note) VALUES(?,?,?)",id,input.quantity(),input.note());
        event(uid(u),number(m,"donor_id"),medicine,id,"REQUESTED","A verified demo NGO requested this listing.");
        event(uid(u),uid(u),medicine,id,"REQUEST_SENT","Your request was sent for donor review.");return Map.of("requestId",id);
    }
    @Transactional public void decideRequest(String email,long requestId,DemoController.Decision input){
        var u=safety.actor(email,"DONOR");var initial=one("SELECT medicine_id FROM requests WHERE request_id=?",requestId);long medicine=number(initial,"medicine_id");
        var m=one("SELECT donor_id,quantity,expiry_date FROM medicines WHERE medicine_id=? FOR UPDATE",medicine);
        if(number(m,"donor_id")!=uid(u))throw denied();
        var r=one("SELECT r.receiver_id,r.status,d.quantity FROM requests r JOIN demo_request_details d ON d.request_id=r.request_id WHERE r.request_id=? FOR UPDATE",requestId);
        if(!"PENDING".equals(r.get("status")))throw bad("Only pending requests can be decided.");
        if(input.approve()){
            var ngo=one("SELECT verified FROM users WHERE user_id=?",r.get("receiver_id"));if(!Boolean.TRUE.equals(ngo.get("verified")))throw bad("NGO is no longer verified.");
            if(((java.sql.Date)m.get("expiry_date")).toLocalDate().isBefore(LocalDate.now()))throw bad("Expired medicine cannot be accepted.");
            int changed=db.update("UPDATE demo_medicine_details SET reserved=reserved+? WHERE medicine_id=? AND reserved+?<=?",r.get("quantity"),medicine,r.get("quantity"),m.get("quantity"));
            if(changed!=1)throw new ApiException(HttpStatus.CONFLICT,"Another accepted request has reserved this quantity.");
        }
        String status=input.approve()?"APPROVED":"DECLINED";
        db.update("UPDATE requests SET status=?,approved_at=CURRENT_TIMESTAMP WHERE request_id=?",status,requestId);
        event(uid(u),number(r,"receiver_id"),medicine,requestId,status,"Request "+(input.approve()?"accepted":"declined")+" by donor. "+input.note());
        event(uid(u),uid(u),medicine,requestId,"REQUEST_"+status,"You "+(input.approve()?"accepted":"declined")+" the NGO request.");
    }
    @Transactional public void schedule(String email,long requestId,DemoController.Schedule input){
        var u=safety.actor(email,"DONOR");var r=one(REQUESTS+"WHERE r.request_id=? FOR UPDATE",requestId);
        if(number(r,"donor_id")!=uid(u))throw denied();if(!"APPROVED".equals(r.get("status")))throw bad("Accept the request before scheduling.");
        if(input.when().isBefore(LocalDateTime.now())||input.when().isAfter(LocalDateTime.now().plusDays(7)))throw bad("Schedule within the next seven days.");
        var expiry=one("SELECT expiry_date FROM medicines WHERE medicine_id=?",r.get("medicine_id"));
        if(input.when().toLocalDate().isAfter(((java.sql.Date)expiry.get("expiry_date")).toLocalDate()))throw bad("Collection cannot be scheduled after expiry.");
        String code=String.format(Locale.ROOT,"%04d",random.nextInt(10000));
        db.update("UPDATE demo_request_details SET scheduled_for=?,collection_note=?,code_hash=?,code_cipher=?,code_expires=?,code_attempts=0,scheduled_at=CURRENT_TIMESTAMP WHERE request_id=?",input.when(),input.note(),passwords.encode(code),encrypt(code),input.when().plusHours(24),requestId);
        event(uid(u),uid(u),number(r,"medicine_id"),requestId,"SCHEDULED","Simulated collection scheduled. Your private code is available in Collections.");
        event(uid(u),number(r,"receiver_id"),number(r,"medicine_id"),requestId,"SCHEDULED","Simulated collection scheduled; no real address or transfer is involved.");
    }
    public Map<String,Object> code(String email,long requestId){
        var u=safety.actor(email,"DONOR");var r=one("SELECT m.donor_id,r.status,d.code_cipher,d.code_expires FROM requests r JOIN medicines m ON m.medicine_id=r.medicine_id JOIN demo_request_details d ON d.request_id=r.request_id WHERE r.request_id=?",requestId);
        if(number(r,"donor_id")!=uid(u))throw denied();
        if(!"APPROVED".equals(r.get("status"))||r.get("code_cipher")==null)throw bad("No active scheduled handoff code.");
        if(time(r.get("code_expires")).isBefore(LocalDateTime.now()))throw bad("Code expired; reschedule to generate a new one.");
        return Map.of("code",decrypt((String)r.get("code_cipher")),"expires",r.get("code_expires"));
    }
    @Transactional(noRollbackFor=ApiException.class) public void collect(String email,long requestId,DemoController.Code input){
        var u=safety.verifiedNgo(email);var r=one("SELECT r.receiver_id,r.medicine_id,r.status,m.donor_id,m.expiry_date,d.code_hash,d.code_expires,d.code_attempts,d.scheduled_for FROM requests r JOIN medicines m ON m.medicine_id=r.medicine_id JOIN demo_request_details d ON d.request_id=r.request_id WHERE r.request_id=? FOR UPDATE",requestId);
        if(number(r,"receiver_id")!=uid(u))throw denied();
        if(!"APPROVED".equals(r.get("status"))||r.get("scheduled_for")==null)throw bad("No scheduled collection.");
        if(((java.sql.Date)r.get("expiry_date")).toLocalDate().isBefore(LocalDate.now()))throw bad("Expired medicine cannot be collected.");
        if(number(r,"code_attempts")>=5)throw bad("Attempt limit reached; donor must reschedule.");
        if(time(r.get("code_expires")).isBefore(LocalDateTime.now()))throw bad("Handoff code expired.");
        if(!passwords.matches(input.code(),(String)r.get("code_hash"))){db.update("UPDATE demo_request_details SET code_attempts=code_attempts+1 WHERE request_id=?",requestId);throw bad("Incorrect code. Collection was not completed.");}
        db.update("UPDATE requests SET status='COLLECTED' WHERE request_id=?",requestId);
        db.update("UPDATE demo_request_details SET completed_at=CURRENT_TIMESTAMP,code_hash=NULL,code_cipher=NULL WHERE request_id=?",requestId);
        event(uid(u),uid(u),number(r,"medicine_id"),requestId,"COMPLETED","Simulated collection completed and recorded as received.");
        event(uid(u),number(r,"donor_id"),number(r,"medicine_id"),requestId,"COMPLETED","Simulated collection completed. No physical medicine was transferred.");
    }
    @Transactional public void apply(String email){
        var u=safety.actor(email,"NGO");if(Boolean.TRUE.equals(u.get("verified")))throw bad("Already verified for fictional demo purposes.");
        db.update("INSERT INTO demo_ngo_applications(ngo_id,status) VALUES(?,'PENDING') ON DUPLICATE KEY UPDATE status='PENDING',applied_at=CURRENT_TIMESTAMP",uid(u));
        admins(uid(u),null,"NGO_APPLICATION","A fictional NGO requested demonstration verification.");
    }
    @Transactional public void verify(String email,long ngo,DemoController.Decision input){
        var u=safety.actor(email,"ADMIN");one("SELECT user_id FROM users WHERE user_id=? AND role='NGO' FOR UPDATE",ngo);
        one("SELECT ngo_id FROM demo_ngo_applications WHERE ngo_id=?",ngo);
        db.update("UPDATE users SET verified=? WHERE user_id=?",input.approve(),ngo);
        db.update("UPDATE demo_ngo_applications SET status=?,reviewed_at=CURRENT_TIMESTAMP WHERE ngo_id=?",input.approve()?"APPROVED":"REJECTED",ngo);
        event(uid(u),ngo,null,null,"NGO_REVIEW","Fictional demo verification "+(input.approve()?"approved":"rejected")+". "+input.note());
    }
    public List<Map<String,Object>> notifications(String email){var u=safety.actor(email,"DONOR","NGO","ADMIN");return db.queryForList("SELECT notif_id,type,message,sent_at,`read` FROM notifications WHERE user_id=? ORDER BY notif_id DESC LIMIT 100",uid(u));}
    public void read(String email,long id){var u=safety.actor(email,"DONOR","NGO","ADMIN");if(db.update("UPDATE notifications SET `read`=true WHERE notif_id=? AND user_id=?",id,uid(u))!=1)throw denied();}
    private byte[] key()throws Exception{return MessageDigest.getInstance("SHA-256").digest((environment.getRequiredProperty("app.jwt.secret")+"|demo-handoff-v1").getBytes(StandardCharsets.UTF_8));}
    private String encrypt(String value){try{byte[] nonce=new byte[12];random.nextBytes(nonce);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key(),"AES"),new GCMParameterSpec(128,nonce));byte[] body=cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));byte[] all=new byte[nonce.length+body.length];System.arraycopy(nonce,0,all,0,nonce.length);System.arraycopy(body,0,all,nonce.length,body.length);return Base64.getEncoder().encodeToString(all);}catch(Exception ex){throw new IllegalStateException("Unable to protect handoff code.");}}
    private String decrypt(String value){try{byte[] all=Base64.getDecoder().decode(value);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key(),"AES"),new GCMParameterSpec(128,Arrays.copyOfRange(all,0,12)));return new String(cipher.doFinal(Arrays.copyOfRange(all,12,all.length)),StandardCharsets.UTF_8);}catch(Exception ex){throw new IllegalStateException("Handoff code unavailable; reschedule locally.");}}
}
