package com.medbridge.demo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/demo")
public class DemoController {
    private final DemoService demo;
    public DemoController(DemoService demo){this.demo=demo;}
    public record MedicineInput(@NotBlank @Size(max=200) String name,@NotBlank @Size(max=50) String form,
        @NotBlank @Size(max=100) String category,@Min(1) @Max(100000) int quantity,
        @NotBlank @Size(max=30) String unit,@NotNull LocalDate expiry,@NotBlank @Size(max=100) String batch,
        @NotBlank @Size(max=300) String storage,boolean sealed,boolean declared,boolean photoConfirmed,@Min(1) long photoId){}
    public record RequestInput(@Min(1) @Max(100000) int quantity,@NotBlank @Size(max=500) String note){}
    public record Decision(boolean approve,@NotBlank @Size(max=300) String note){}
    public record Schedule(@NotNull LocalDateTime when,@NotBlank @Size(max=200) String note){}
    public record Code(@NotNull @Pattern(regexp="[0-9]{4}") String code){}
    @GetMapping("/summary") public Map<String,Object> summary(Authentication a){return demo.summary(a.getName());}
    @GetMapping("/medicines") public List<Map<String,Object>> medicines(Authentication a){return demo.medicines(a.getName());}
    @PostMapping("/medicines") @ResponseStatus(HttpStatus.CREATED) public Map<String,Object> submit(Authentication a,@Valid @RequestBody MedicineInput input){return demo.submit(a.getName(),input);}
    @PostMapping(value="/photos",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> upload(Authentication a,@RequestPart("file") MultipartFile file){return demo.upload(a.getName(),file);}
    @GetMapping(value="/photos/{id}",produces=MediaType.IMAGE_PNG_VALUE) public ResponseEntity<byte[]> photo(Authentication a,@PathVariable long id){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").body(demo.photo(a.getName(),id));}
    @GetMapping("/medicines/{id}/events") public List<Map<String,Object>> events(Authentication a,@PathVariable long id){return demo.medicineEvents(a.getName(),id);}
    @PostMapping("/medicines/{id}/review") public void review(Authentication a,@PathVariable long id,@Valid @RequestBody Decision input){demo.review(a.getName(),id,input);}
    @GetMapping("/browse") public List<Map<String,Object>> browse(Authentication a,@RequestParam(defaultValue="10") double radius,@RequestParam(defaultValue="0") int months,@RequestParam(defaultValue="") String category){return demo.browse(a.getName(),radius,months,category);}
    @GetMapping("/requests") public List<Map<String,Object>> requests(Authentication a){return demo.requests(a.getName());}
    @PostMapping("/medicines/{id}/requests") @ResponseStatus(HttpStatus.CREATED) public Map<String,Object> request(Authentication a,@PathVariable long id,@Valid @RequestBody RequestInput input){return demo.request(a.getName(),id,input);}
    @PostMapping("/requests/{id}/decision") public void decision(Authentication a,@PathVariable long id,@Valid @RequestBody Decision input){demo.decideRequest(a.getName(),id,input);}
    @PostMapping("/requests/{id}/schedule") public void schedule(Authentication a,@PathVariable long id,@Valid @RequestBody Schedule input){demo.schedule(a.getName(),id,input);}
    @GetMapping("/requests/{id}/code") public ResponseEntity<Map<String,Object>> code(Authentication a,@PathVariable long id){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(demo.code(a.getName(),id));}
    @PostMapping("/requests/{id}/collect") public void collect(Authentication a,@PathVariable long id,@Valid @RequestBody Code input){demo.collect(a.getName(),id,input);}
    @PostMapping("/ngo-application") public void apply(Authentication a){demo.apply(a.getName());}
    @PostMapping("/ngos/{id}/review") public void verify(Authentication a,@PathVariable long id,@Valid @RequestBody Decision input){demo.verify(a.getName(),id,input);}
    @GetMapping("/notifications") public List<Map<String,Object>> notifications(Authentication a){return demo.notifications(a.getName());}
    @PostMapping("/notifications/{id}/read") public void read(Authentication a,@PathVariable long id){demo.read(a.getName(),id);}
}
