package com.wuyuxiao.aippt.service;

import com.wuyuxiao.aippt.domain.*;
import com.wuyuxiao.aippt.repo.PresentationRepository;
import com.wuyuxiao.aippt.web.ApiModels.*;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class PresentationService {
    private final PresentationRepository repo; private final AgentScopePptService agents; private final Executor executor;
    public PresentationService(PresentationRepository repo,AgentScopePptService agents,@Qualifier("slideExecutor") Executor executor){this.repo=repo;this.agents=agents;this.executor=executor;}
    @Transactional
    public Presentation create(String title,String source,int slideCount){
        int count=Math.max(3,Math.min(20,slideCount)); Presentation p=new Presentation(); p.setSourceText(source);
        var plan=agents.plan(title,source,count,UUID.randomUUID().toString()); p.setTitle(plan.title());
        int i=1; for(var item:plan.slides()){ Slide s=new Slide();s.setPosition(i++);s.setTitle(item.title());s.setSubtitle(item.subtitle());s.setLayout(item.layout());p.addSlide(s); }
        p.setProgress(0); return repo.save(p);
    }
    @Transactional public Presentation update(String id,OutlineUpdate update){
        Presentation p=get(id);p.setTitle(update.title());if(update.theme()!=null)p.setTheme(update.theme());List<Slide> slides=new ArrayList<>();int i=1;
        for(OutlineItem item:update.slides()){Slide s=new Slide();s.setPosition(i++);s.setTitle(item.title());s.setSubtitle(item.subtitle());s.setLayout(item.layout()==null?"content":item.layout());slides.add(s);}p.replaceSlides(slides);return repo.save(p);
    }
    public Presentation get(String id){return repo.findById(id).orElseThrow(()->new NoSuchElementException("项目不存在"));}
    @Async
    public void generate(String id){
        Presentation p=get(id);p.setStatus(Presentation.Status.GENERATING);p.setProgress(1);repo.save(p);String session=id;AtomicInteger done=new AtomicInteger();int total=p.getSlides().size();
        try{
            List<CompletableFuture<Void>> tasks=p.getSlides().stream().map(slide->CompletableFuture.runAsync(()->{
                var content=agents.write(slide,p.getSourceText(),session+"-"+slide.getPosition());slide.setSubtitle(content.subtitle());slide.setBullets(String.join("\n",content.bullets()));slide.setSpeakerNotes(content.speakerNotes());slide.setLayout(agents.chooseLayout(slide,session));slide.setGenerated(true);
                synchronized(p){p.setProgress(Math.min(95,done.incrementAndGet()*95/total));repo.save(p);}
            },executor)).toList(); CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).join();p.setProgress(100);p.setStatus(Presentation.Status.COMPLETED);repo.save(p);
        }catch(Exception e){p.setStatus(Presentation.Status.FAILED);p.setErrorMessage(e.getMessage());repo.save(p);}
    }
}
