package com.wuyuxiao.aippt.service;

import com.fasterxml.jackson.databind.*;
import com.wuyuxiao.aippt.domain.Slide;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.*;

/** AgentScope multi-agent facade: planner, writer, visual designer and reviewer. */
@Service
public class AgentScopePptService {
    private final ObjectMapper json;
    private final String apiKey, baseUrl, modelName;
    private final double temperature;
    public AgentScopePptService(ObjectMapper json, @Value("${app.ai.api-key:}") String apiKey,
            @Value("${app.ai.base-url}") String baseUrl, @Value("${app.ai.model}") String modelName,
            @Value("${app.ai.temperature:0.5}") double temperature) {
        this.json=json; this.apiKey=apiKey; this.baseUrl=baseUrl; this.modelName=modelName; this.temperature=temperature;
    }
    public boolean enabled() { return apiKey != null && !apiKey.isBlank(); }
    private Model model() {
        return OpenAIChatModel.builder().apiKey(apiKey).baseUrl(baseUrl).modelName(modelName).stream(false)
            .generateOptions(GenerateOptions.builder().temperature(temperature).build()).build();
    }
    private String call(String role, String prompt, String session) {
        RuntimeContext runtime=RuntimeContext.builder().sessionId(session).userId("ppt-user").build();
        try (ReActAgent agent=ReActAgent.builder().name(role+"-"+UUID.randomUUID()).model(model()).maxIters(2)
                .sysPrompt(switch(role) {
                    case "planner" -> "你是演示文稿策划智能体，擅长把材料变成逻辑清晰、有叙事节奏的大纲。只返回合法JSON。";
                    case "writer" -> "你是PPT内容智能体。内容简洁、可信、适合屏幕阅读。只返回合法JSON。";
                    case "designer" -> "你是PPT视觉设计智能体。根据页面语义选择版式。只返回合法JSON。";
                    default -> "你是PPT审校智能体。删除重复和空话，不编造材料外数据。只返回合法JSON。";
                }).build()) {
            Msg response=agent.call(prompt,runtime).block();
            return response == null ? "" : response.getTextContent();
        }
    }
    public Plan plan(String requestedTitle,String source,int count,String session) {
        if (!enabled()) return fallbackPlan(requestedTitle,source,count);
        String prompt="根据材料生成"+count+"页大纲。JSON格式：{\"title\":\"...\",\"slides\":[{\"title\":\"...\",\"subtitle\":\"...\",\"layout\":\"cover|content|quote|summary\"}]}。材料：\n"+clip(source,18000);
        try { return json.readValue(cleanJson(call("planner",prompt,session)),Plan.class); }
        catch(Exception ignored) { return fallbackPlan(requestedTitle,source,count); }
    }
    public SlideContent write(Slide slide,String source,String session) {
        if (!enabled()) return fallbackContent(slide,source);
        String prompt="为第"+slide.getPosition()+"页《"+slide.getTitle()+"》写内容。JSON格式：{\"subtitle\":\"一句结论\",\"bullets\":[\"要点1\",\"要点2\",\"要点3\"],\"speakerNotes\":\"讲稿\"}。只用给定材料：\n"+clip(source,14000);
        try {
            SlideContent draft=json.readValue(cleanJson(call("writer",prompt,session)),SlideContent.class);
            String review="审校下面内容，保持同一JSON格式；每个要点不超过35字，不得补充材料外事实。材料：\n"+clip(source,8000)+"\n草稿：\n"+json.writeValueAsString(draft);
            return json.readValue(cleanJson(call("reviewer",review,session)),SlideContent.class);
        } catch(Exception ignored) { return fallbackContent(slide,source); }
    }
    public String chooseLayout(Slide slide,String session) {
        if (!enabled() || slide.getPosition()==1) return slide.getPosition()==1?"cover":slide.getLayout();
        try {
            JsonNode n=json.readTree(cleanJson(call("designer","为页面选择版式，只返回{\"layout\":\"content|quote|summary\"}。标题："+slide.getTitle(),session)));
            String value=n.path("layout").asText("content");
            return Set.of("content","quote","summary").contains(value)?value:"content";
        } catch(Exception ignored){ return "content"; }
    }
    private Plan fallbackPlan(String requested,String source,int count) {
        String title=(requested==null||requested.isBlank())?firstMeaningful(source,"智能演示"):requested.strip();
        List<PlanItem> items=new ArrayList<>(); items.add(new PlanItem(title,"AI 为你生成的可编辑演示文稿","cover"));
        String[] heads={"背景与挑战","核心洞察","关键方案","实施路径","案例与应用","价值与收益","风险与对策","行动建议"};
        for(int i=1;i<count-1;i++) items.add(new PlanItem(heads[(i-1)%heads.length],"从材料中提炼第 "+i+" 个关键信息","content"));
        if(count>1) items.add(new PlanItem("总结与下一步","凝聚共识，推动行动","summary"));
        return new Plan(title,items);
    }
    private SlideContent fallbackContent(Slide slide,String source) {
        List<String> sentences=Arrays.stream(source.replaceAll("[\\r\\n]+","。 ").split("[。！？.!?]"))
            .map(String::strip).filter(s->s.length()>8).limit(24).toList();
        int start=Math.max(0,(slide.getPosition()-2)*3); List<String> bullets=new ArrayList<>();
        for(int i=start;i<Math.min(sentences.size(),start+3);i++) bullets.add(clip(sentences.get(i),35));
        if(bullets.isEmpty()) bullets=List.of("聚焦主题，明确本页核心观点","用结构化表达降低理解成本","结合实际场景推动内容落地");
        return new SlideContent(slide.getSubtitle(),bullets,"围绕“"+slide.getTitle()+"”展开说明，可结合实际业务补充案例。");
    }
    private String firstMeaningful(String s,String fallback){ if(s==null)return fallback; return Arrays.stream(s.split("[\\r\\n。]" )).map(String::strip).filter(x->x.length()>2).findFirst().map(x->clip(x,28)).orElse(fallback); }
    private static String clip(String s,int n){ if(s==null)return ""; return s.length()>n?s.substring(0,n):s; }
    private static String cleanJson(String s){ int a=s.indexOf('{'),b=s.lastIndexOf('}'); return a>=0&&b>a?s.substring(a,b+1):s; }
    public record Plan(String title,List<PlanItem> slides){}
    public record PlanItem(String title,String subtitle,String layout){}
    public record SlideContent(String subtitle,List<String> bullets,String speakerNotes){}
}

