package com.wuyuxiao.aippt.domain;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.*;

@Entity @Table(name="presentations")
public class Presentation {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private String id;
    @Column(nullable=false) private String title;
    @Lob @Column(columnDefinition="LONGTEXT") private String sourceText;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private Status status = Status.OUTLINE_READY;
    private int progress;
    private String theme = "midnight";
    private String errorMessage;
    private LocalDateTime createdAt = LocalDateTime.now();
    @JsonManagedReference @OneToMany(mappedBy="presentation", cascade=CascadeType.ALL, orphanRemoval=true, fetch=FetchType.EAGER)
    @OrderBy("position asc") private List<Slide> slides = new ArrayList<>();
    public enum Status { OUTLINE_READY, GENERATING, COMPLETED, FAILED, CANCELLED }
    public void replaceSlides(List<Slide> items) { slides.clear(); items.forEach(this::addSlide); }
    public void addSlide(Slide slide) { slide.setPresentation(this); slides.add(slide); }
    public String getId(){return id;} public String getTitle(){return title;} public void setTitle(String v){title=v;}
    public String getSourceText(){return sourceText;} public void setSourceText(String v){sourceText=v;}
    public Status getStatus(){return status;} public void setStatus(Status v){status=v;}
    public int getProgress(){return progress;} public void setProgress(int v){progress=v;}
    public String getTheme(){return theme;} public void setTheme(String v){theme=v;}
    public String getErrorMessage(){return errorMessage;} public void setErrorMessage(String v){errorMessage=v;}
    public LocalDateTime getCreatedAt(){return createdAt;} public List<Slide> getSlides(){return slides;}
}
