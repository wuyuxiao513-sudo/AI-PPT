package com.wuyuxiao.aippt.service;

import com.wuyuxiao.aippt.domain.Presentation;
import com.wuyuxiao.aippt.domain.Slide;
import org.apache.poi.sl.usermodel.ShapeType;
import org.apache.poi.xslf.usermodel.*;
import org.springframework.stereotype.Service;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.io.*;
import java.util.List;

@Service
public class PptxExportService {
    private static final Color NAVY=new Color(13,24,45), BLUE=new Color(67,97,238), CYAN=new Color(76,201,240), WHITE=Color.WHITE, MUTED=new Color(180,193,214);
    public byte[] export(Presentation presentation) {
        try (XMLSlideShow deck=new XMLSlideShow();ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            deck.setPageSize(new Dimension(960,540));
            for(Slide item:presentation.getSlides()) render(deck,item,presentation.getTitle());
            deck.write(out);return out.toByteArray();
        } catch(IOException e){throw new IllegalStateException("PPTX 导出失败",e);}
    }
    private void render(XMLSlideShow deck,Slide item,String deckTitle){
        XSLFSlide slide=deck.createSlide(); background(slide,NAVY);
        if("cover".equals(item.getLayout())||item.getPosition()==1){
            rect(slide,70,90,8,300,CYAN); text(slide,item.getTitle(),100,115,760,150,36,WHITE,true);
            text(slide,blank(item.getSubtitle(),"AI 智能生成 · 原生可编辑"),105,285,650,55,18,MUTED,false);
            text(slide,"AI PRESENTATION",760,470,140,28,10,CYAN,true);
        }else{
            text(slide,String.format("%02d",item.getPosition()),70,45,80,35,16,CYAN,true);
            text(slide,item.getTitle(),70,82,820,70,28,WHITE,true);
            rect(slide,70,155,120,5,BLUE);
            String subtitle=blank(item.getSubtitle(),"核心观点"); text(slide,subtitle,70,178,800,55,18,CYAN,true);
            if("quote".equals(item.getLayout())){
                text(slide,"“",65,225,60,70,48,BLUE,true);text(slide,firstBullet(item),120,245,720,145,25,WHITE,false);
            }else{ bullets(slide,item.getBullets(),90,245,760,210); }
            text(slide,deckTitle,70,500,650,20,9,MUTED,false);text(slide,String.valueOf(item.getPosition()),860,500,30,20,9,MUTED,false);
        }
    }
    private void bullets(XSLFSlide slide,String value,double x,double y,double w,double h){
        XSLFTextBox box=slide.createTextBox();box.setAnchor(new Rectangle2D.Double(x,y,w,h));box.clearText();
        List<String> values=value==null||value.isBlank()?List.of("内容生成完成，可在大纲页继续修改"):value.lines().filter(s->!s.isBlank()).toList();
        for(String line:values){XSLFTextParagraph p=box.addNewTextParagraph();p.setBullet(true);p.setBulletFontColor(CYAN);p.setSpaceAfter(12d);XSLFTextRun r=p.addNewTextRun();r.setText(line);r.setFontFamily("Microsoft YaHei");r.setFontSize(20d);r.setFontColor(WHITE);}
    }
    private void background(XSLFSlide slide,Color color){XSLFAutoShape s=slide.createAutoShape();s.setShapeType(ShapeType.RECT);s.setAnchor(new Rectangle2D.Double(0,0,960,540));s.setFillColor(color);s.setLineColor(color);}
    private void rect(XSLFSlide slide,double x,double y,double w,double h,Color color){XSLFAutoShape s=slide.createAutoShape();s.setShapeType(ShapeType.RECT);s.setAnchor(new Rectangle2D.Double(x,y,w,h));s.setFillColor(color);s.setLineColor(color);}
    private void text(XSLFSlide slide,String value,double x,double y,double w,double h,double size,Color color,boolean bold){XSLFTextBox b=slide.createTextBox();b.setAnchor(new Rectangle2D.Double(x,y,w,h));XSLFTextRun r=b.setText(blank(value," "));r.setFontFamily("Microsoft YaHei");r.setFontSize(size);r.setFontColor(color);r.setBold(bold);b.setWordWrap(true);}
    private String firstBullet(Slide s){return s.getBullets()==null?s.getSubtitle():s.getBullets().lines().findFirst().orElse(s.getSubtitle());}
    private String blank(String v,String fallback){return v==null||v.isBlank()?fallback:v;}
}
