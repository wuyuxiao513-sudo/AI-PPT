package com.wuyuxiao.aippt;

import com.wuyuxiao.aippt.domain.Presentation;
import com.wuyuxiao.aippt.domain.Slide;
import com.wuyuxiao.aippt.repo.PresentationRepository;
import com.wuyuxiao.aippt.service.AgentScopePptService;
import com.wuyuxiao.aippt.service.PresentationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
class PresentationCancellationTest {
    @Autowired private PresentationService service;
    @Autowired private PresentationRepository repo;
    @MockBean private AgentScopePptService agents;

    @Test
    void stopPreventsLateSlideWrites() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(agents.write(any(Slide.class), anyString(), anyString())).thenAnswer(invocation -> {
            entered.countDown();
            release.await(5, TimeUnit.SECONDS);
            return new AgentScopePptService.SlideContent("late", List.of("late"), "late");
        });
        Presentation p = createProject();
        assertThat(service.startGeneration(p.getId())).isTrue();
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        try {
            assertThat(service.cancelGeneration(p.getId()).getStatus()).isEqualTo(Presentation.Status.CANCELLED);
        } finally { release.countDown(); }
        awaitStatus(p.getId(), Presentation.Status.CANCELLED);
        Thread.sleep(200);
        Presentation stopped = service.get(p.getId());
        assertThat(stopped.getStatus()).isEqualTo(Presentation.Status.CANCELLED);
        assertThat(stopped.getSlides()).noneMatch(Slide::isGenerated);
    }

    @Test
    void deletingActiveProjectPreventsRecreation() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(agents.write(any(Slide.class), anyString(), anyString())).thenAnswer(invocation -> {
            entered.countDown();
            release.await(5, TimeUnit.SECONDS);
            return new AgentScopePptService.SlideContent("late", List.of("late"), "late");
        });
        Presentation p = createProject();
        assertThat(service.startGeneration(p.getId())).isTrue();
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        try { service.delete(p.getId()); } finally { release.countDown(); }
        Thread.sleep(200);
        assertThat(repo.findById(p.getId())).isEmpty();
    }

    private Presentation createProject() {
        Presentation p = new Presentation();
        p.setTitle("test");
        p.setSourceText("source");
        for (int i = 1; i <= 3; i++) {
            Slide s = new Slide(); s.setPosition(i); s.setTitle("slide " + i); p.addSlide(s);
        }
        return repo.saveAndFlush(p);
    }

    private void awaitStatus(String id, Presentation.Status status) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (Instant.now().isBefore(deadline)) {
            if (service.get(id).getStatus() == status) return;
            Thread.sleep(25);
        }
        assertThat(service.get(id).getStatus()).isEqualTo(status);
    }
}
