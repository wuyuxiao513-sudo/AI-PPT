package com.wuyuxiao.aippt.web;

import com.wuyuxiao.aippt.domain.Presentation;
import com.wuyuxiao.aippt.repo.PresentationRepository;
import com.wuyuxiao.aippt.service.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.task.TaskRejectedException;

@RestController @RequestMapping("/api/presentations")
public class PresentationController {
    private final PresentationService service;private final DocumentTextService documents;private final PptxExportService export;private final PresentationRepository repo;
    public PresentationController(PresentationService s,DocumentTextService d,PptxExportService e,PresentationRepository r){service=s;documents=d;export=e;repo=r;}
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Presentation create(@RequestParam(required=false) String title,@RequestParam(required=false) String text,@RequestParam(defaultValue="8") int slideCount,@RequestPart(required=false) MultipartFile file){
        String source=(text==null?"":text.strip());String uploaded=documents.extract(file);if(!uploaded.isBlank())source=source.isBlank()?uploaded:source+"\n\n"+uploaded;
        if(source.isBlank()&&(title==null||title.isBlank()))throw new IllegalArgumentException("请输入主题、粘贴文本或上传文档");if(source.isBlank())source=title;return service.create(title,source,slideCount);
    }
    @GetMapping public List<Presentation> list(){return repo.findAll(Sort.by(Sort.Direction.DESC,"createdAt"));}
    @GetMapping("/{id}") public Presentation get(@PathVariable String id){return service.get(id);}
    @PutMapping("/{id}/outline") public Presentation update(@PathVariable String id,@Valid @RequestBody ApiModels.OutlineUpdate body){return service.update(id,body);}
    @PostMapping("/{id}/generate") public ResponseEntity<Void> generate(@PathVariable String id){
        if(service.prepareGeneration(id)) {
            try { service.generate(id); }
            catch(TaskRejectedException e) {
                service.failGeneration(id,"生成任务繁忙，请稍后重试");
                throw new IllegalStateException("生成任务繁忙，请稍后重试",e);
            }
        }
        return ResponseEntity.accepted().build();
    }
    @GetMapping("/{id}/download") public ResponseEntity<byte[]> download(@PathVariable String id){Presentation p=service.get(id);if(p.getStatus()!=Presentation.Status.COMPLETED)throw new IllegalStateException("页面尚未生成完成");String name=URLEncoder.encode(p.getTitle()+".pptx",StandardCharsets.UTF_8).replace("+","%20");return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename*=UTF-8''"+name).contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.presentationml.presentation")).body(export.export(p));}
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable String id){repo.delete(service.get(id));return ResponseEntity.noContent().build();}
}
