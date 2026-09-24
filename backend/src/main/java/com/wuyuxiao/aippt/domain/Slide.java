package com.wuyuxiao.aippt.domain;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;

@Entity @Table(name="slides")
public class Slide {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @JsonBackReference @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="presentation_id",nullable=false) private Presentation presentation;
    private int position;
    @Column(nullable=false) private String title;
    private String subtitle;
    @Lob @Column(columnDefinition="TEXT") private String bullets;
    @Lob @Column(columnDefinition="TEXT") private String speakerNotes;
    private String layout = "content";
    private boolean generated;
    public Long getId(){return id;} public int getPosition(){return position;} public void setPosition(int v){position=v;}
    public String getTitle(){return title;} public void setTitle(String v){title=v;}
    public String getSubtitle(){return subtitle;} public void setSubtitle(String v){subtitle=v;}
    public String getBullets(){return bullets;} public void setBullets(String v){bullets=v;}
    public String getSpeakerNotes(){return speakerNotes;} public void setSpeakerNotes(String v){speakerNotes=v;}
    public String getLayout(){return layout;} public void setLayout(String v){layout=v;}
    public boolean isGenerated(){return generated;} public void setGenerated(boolean v){generated=v;}
    public void setPresentation(Presentation v){presentation=v;}
}

