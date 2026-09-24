package com.wuyuxiao.aippt;
import com.wuyuxiao.aippt.domain.*;
import com.wuyuxiao.aippt.service.PptxExportService;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import static org.assertj.core.api.Assertions.assertThat;
class PptxExportServiceTest {
    @Test void exportsEditableNativeSlides() throws Exception {
        Presentation p=new Presentation();p.setTitle("测试演示");Slide s=new Slide();s.setPosition(1);s.setTitle("封面");s.setLayout("cover");p.addSlide(s);Slide s2=new Slide();s2.setPosition(2);s2.setTitle("洞察");s2.setBullets("要点一\n要点二");p.addSlide(s2);
        byte[] bytes=new PptxExportService().export(p);assertThat(bytes).isNotEmpty();try(XMLSlideShow show=new XMLSlideShow(new ByteArrayInputStream(bytes))){assertThat(show.getSlides()).hasSize(2);assertThat(show.getSlides().get(1).getShapes()).hasSizeGreaterThan(2);}
    }
}
