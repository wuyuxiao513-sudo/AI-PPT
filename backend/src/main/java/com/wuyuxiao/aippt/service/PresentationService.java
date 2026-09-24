package com.wuyuxiao.aippt.service;

import com.wuyuxiao.aippt.domain.*;
import com.wuyuxiao.aippt.repo.PresentationRepository;
import com.wuyuxiao.aippt.web.ApiModels.*;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.*;

@Service
public class PresentationService {
    private final PresentationRepository repo; private final AgentScopePptService agents;
    private final ThreadPoolTaskExecutor slideExecutor; private final ThreadPoolTaskExecutor generationExecutor;
    private final ConcurrentMap<String, GenerationRun> activeGenerations = new ConcurrentHashMap<>();
    public PresentationService(PresentationRepository repo, AgentScopePptService agents,
                               @Qualifier("slideExecutor") ThreadPoolTaskExecutor slideExecutor,
                               @Qualifier("generationExecutor") ThreadPoolTaskExecutor generationExecutor) {
        this.repo=repo; this.agents=agents; this.slideExecutor=slideExecutor; this.generationExecutor=generationExecutor;
    }
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
    public boolean startGeneration(String id) {
        GenerationRun run = new GenerationRun();
        if (activeGenerations.putIfAbsent(id, run) != null) return false;
        try {
            synchronized (run.lock) {
                Presentation p = get(id);
                p.setStatus(Presentation.Status.GENERATING);
                p.setProgress(0);
                p.setErrorMessage(null);
                repo.saveAndFlush(p);
                run.add(generationExecutor.submit(() -> generate(id, run)));
            }
            return true;
        } catch (RuntimeException e) {
            synchronized (run.lock) {
                run.cancelled = true;
                repo.findById(id).ifPresent(p -> {
                    if (p.getStatus() == Presentation.Status.GENERATING) {
                        p.setStatus(Presentation.Status.FAILED);
                        p.setErrorMessage("生成任务提交失败，请稍后重试");
                        repo.save(p);
                    }
                });
            }
            activeGenerations.remove(id, run);
            throw e;
        }
    }
    public Presentation cancelGeneration(String id) {
        GenerationRun run = activeGenerations.get(id);
        if (run == null) {
            Presentation p = get(id);
            if (p.getStatus() == Presentation.Status.GENERATING) {
                p.setStatus(Presentation.Status.CANCELLED);
                return repo.saveAndFlush(p);
            }
            return p;
        }
        synchronized (run.lock) {
            run.cancel();
            Presentation p = get(id);
            if (p.getStatus() == Presentation.Status.GENERATING) {
                p.setStatus(Presentation.Status.CANCELLED);
                return repo.saveAndFlush(p);
            }
            return p;
        }
    }
    public void delete(String id) {
        GenerationRun run = activeGenerations.get(id);
        if (run == null) { repo.delete(get(id)); return; }
        synchronized (run.lock) {
            run.cancel();
            repo.delete(get(id));
            repo.flush();
            activeGenerations.remove(id, run);
        }
    }
    private void generate(String id, GenerationRun run) {
        try {
            if (run.cancelled) return;
            Presentation p = get(id);
            int total = p.getSlides().size();
            List<Future<?>> tasks = new ArrayList<>();
            for (Slide slide : p.getSlides()) {
                if (run.cancelled) return;
                Future<?> task = slideExecutor.submit(() -> generateSlide(p, slide, total, run));
                tasks.add(task);
                run.add(task);
            }
            for (Future<?> task : tasks) task.get();
            synchronized (run.lock) {
                if (run.cancelled) return;
                p.setProgress(100);
                p.setStatus(Presentation.Status.COMPLETED);
                repo.saveAndFlush(p);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (CancellationException e) {
            // Cancellation is an expected outcome when the user stops or deletes a project.
        } catch (Exception e) {
            synchronized (run.lock) {
                if (!run.cancelled) repo.findById(id).ifPresent(p -> {
                    p.setStatus(Presentation.Status.FAILED);
                    p.setErrorMessage(e.getCause() == null ? e.getMessage() : e.getCause().getMessage());
                    repo.saveAndFlush(p);
                });
            }
        } finally {
            activeGenerations.remove(id, run);
        }
    }
    private void generateSlide(Presentation p, Slide slide, int total, GenerationRun run) {
        if (run.cancelled || Thread.currentThread().isInterrupted()) return;
        String session = p.getId() + "-" + slide.getPosition();
        var content = agents.write(slide, p.getSourceText(), session);
        if (run.cancelled || Thread.currentThread().isInterrupted()) return;
        String layout = agents.chooseLayout(slide, session);
        synchronized (run.lock) {
            if (run.cancelled || Thread.currentThread().isInterrupted()) return;
            slide.setSubtitle(content.subtitle());
            slide.setBullets(String.join("\n", content.bullets()));
            slide.setSpeakerNotes(content.speakerNotes());
            slide.setLayout(layout);
            slide.setGenerated(true);
            long done = p.getSlides().stream().filter(Slide::isGenerated).count();
            p.setProgress(Math.min(95, (int) (done * 95 / total)));
            repo.saveAndFlush(p);
        }
    }
    private static final class GenerationRun {
        final Object lock = new Object();
        final List<Future<?>> futures = new ArrayList<>();
        volatile boolean cancelled;
        void add(Future<?> future) {
            synchronized (lock) {
                futures.add(future);
                if (cancelled) future.cancel(true);
            }
        }
        void cancel() {
            cancelled = true;
            for (Future<?> future : futures) future.cancel(true);
        }
    }
}
