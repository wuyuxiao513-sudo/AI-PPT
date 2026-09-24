package com.wuyuxiao.aippt;

import com.wuyuxiao.aippt.domain.Presentation;
import com.wuyuxiao.aippt.repo.PresentationRepository;
import com.wuyuxiao.aippt.service.PresentationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PresentationGenerationTest {
    @Autowired private PresentationService service;
    @Autowired private PresentationRepository repo;

    @Test
    void acceptsOneGenerationAndCompletesAllSlides() throws Exception {
        Presentation created = service.create("低碳旅行平台", "面向年轻人的低碳旅行平台，提供火车路线、绿色住宿与碳足迹记录。", 5);
        created.setStatus(Presentation.Status.GENERATING); // Simulate a task left by a previous process.
        repo.save(created);
        assertThat(service.startGeneration(created.getId())).isTrue();
        assertThat(service.startGeneration(created.getId())).isFalse();
        Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
        Presentation current;
        do {
            Thread.sleep(100);
            current = repo.findById(created.getId()).orElseThrow();
        } while (current.getStatus() == Presentation.Status.GENERATING && Instant.now().isBefore(deadline));

        assertThat(current.getStatus()).isEqualTo(Presentation.Status.COMPLETED);
        assertThat(current.getProgress()).isEqualTo(100);
        assertThat(current.getSlides()).allMatch(slide -> slide.isGenerated());
    }
}
