<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

type Status='OUTLINE_READY'|'GENERATING'|'COMPLETED'|'FAILED'
interface Slide {id?:number;position:number;title:string;subtitle:string;bullets?:string;layout:string;generated:boolean}
interface Project {id:string;title:string;status:Status;progress:number;theme:string;errorMessage?:string;slides:Slide[];createdAt:string}
const mode=ref<'topic'|'text'|'file'>('topic'),title=ref(''),text=ref(''),slideCount=ref(8),file=ref<File|null>(null)
const project=ref<Project|null>(null),history=ref<Project[]>([]),busy=ref(false),error=ref(''),activeSlide=ref(0)
const stage=computed(()=>!project.value?'input':project.value.status==='OUTLINE_READY'?'outline':project.value.status==='GENERATING'?'generating':'result')
const inputReady=computed(()=>mode.value==='topic'?title.value.trim():mode.value==='text'?text.value.trim():!!file.value)

async function request<T>(url:string,options?:RequestInit):Promise<T>{const res=await fetch(url,options);if(!res.ok){const body=await res.json().catch(()=>({}));throw new Error(body.message||'请求失败')}const body=await res.text();return body.trim()?JSON.parse(body) as T:undefined as T}
async function loadHistory(){history.value=await request<Project[]>('/api/presentations').catch(()=>[])}
function chooseFile(e:Event){file.value=(e.target as HTMLInputElement).files?.[0]||null}
async function create(){if(!inputReady.value)return;busy.value=true;error.value='';try{const data=new FormData();if(title.value)data.append('title',title.value);if(mode.value==='text')data.append('text',text.value);if(mode.value==='file'&&file.value)data.append('file',file.value);data.append('slideCount',String(slideCount.value));project.value=await request('/api/presentations',{method:'POST',body:data});activeSlide.value=0;loadHistory()}catch(e){error.value=(e as Error).message}finally{busy.value=false}}
function addSlide(){if(!project.value)return;project.value.slides.push({position:project.value.slides.length+1,title:'新页面',subtitle:'填写本页意图',layout:'content',generated:false})}
function removeSlide(index:number){if(!project.value||project.value.slides.length<=3)return;project.value.slides.splice(index,1);project.value.slides.forEach((s,i)=>s.position=i+1)}
async function generate(){if(!project.value)return;busy.value=true;error.value='';try{const p=project.value;const updated=await request<Project>(`/api/presentations/${p.id}/outline`,{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({title:p.title,theme:p.theme,slides:p.slides.map(s=>({title:s.title,subtitle:s.subtitle,layout:s.layout}))})});await request(`/api/presentations/${p.id}/generate`,{method:'POST'});updated.status='GENERATING';project.value=updated;poll()}catch(e){error.value=(e as Error).message}finally{busy.value=false}}
async function poll(){if(!project.value)return;const current=await request<Project>(`/api/presentations/${project.value.id}`);project.value=current;if(current.status==='GENERATING')setTimeout(poll,1000);else loadHistory()}
function openProject(p:Project){project.value=p;activeSlide.value=0;if(p.status==='GENERATING')poll()}
function reset(){project.value=null;title.value='';text.value='';file.value=null;error.value=''}
function download(){if(project.value)window.location.href=`/api/presentations/${project.value.id}/download`}
onMounted(loadHistory)
</script>

<template>
  <div class="shell">
    <aside class="sidebar">
      <div class="brand"><span class="brand-mark">P</span><div><b>PresentMind</b><small>AI STORY STUDIO</small></div></div>
      <button class="new-button" @click="reset"><span>＋</span> 新建演示文稿</button>
      <div class="history-title">最近项目</div>
      <button v-for="item in history" :key="item.id" class="history-item" :class="{active:item.id===project?.id}" @click="openProject(item)">
        <span class="doc-icon">▤</span><span><b>{{ item.title }}</b><small>{{ item.slides.length }} 页 · {{ item.status==='COMPLETED'?'已完成':'草稿' }}</small></span>
      </button>
      <div class="sidebar-foot"><span class="status-dot"></span> AgentScope 已就绪</div>
    </aside>

    <main>
      <header><div class="crumb">工作台 <span>/</span> {{ project?.title || '新建演示' }}</div><div class="avatar">AI</div></header>
      <section v-if="stage==='input'" class="hero">
        <div class="eyebrow"><span></span> FROM IDEA TO DECK</div>
        <h1>一句话，生成一份<br><em>有说服力</em>的演示文稿</h1>
        <p>输入主题或导入材料。AI 智能体负责策划、写作、设计与审校，<br>最终导出每个元素都可编辑的 PowerPoint 文件。</p>
        <div class="composer">
          <div class="tabs"><button :class="{active:mode==='topic'}" @click="mode='topic'">灵感主题</button><button :class="{active:mode==='text'}" @click="mode='text'">长文本</button><button :class="{active:mode==='file'}" @click="mode='file'">导入文档</button></div>
          <textarea v-if="mode==='topic'" v-model="title" rows="3" placeholder="例如：为投资人介绍一个面向年轻人的低碳旅行平台"></textarea>
          <textarea v-else-if="mode==='text'" v-model="text" rows="7" placeholder="粘贴会议纪要、文章、产品资料或调研报告……"></textarea>
          <label v-else class="dropzone"><input type="file" accept=".pdf,.doc,.docx,.md,.markdown,.txt" @change="chooseFile"><span class="upload-icon">↑</span><b>{{ file?.name || '点击选择文档' }}</b><small>支持 PDF、Word、Markdown、TXT · 最大 30MB</small></label>
          <div class="composer-foot"><label>页数 <select v-model="slideCount"><option v-for="n in [5,6,8,10,12,15]" :key="n" :value="n">{{ n }} 页</option></select></label><button class="primary" :disabled="!inputReady||busy" @click="create"><span v-if="busy" class="spinner"></span>{{ busy?'正在策划…':'生成大纲' }} <span>→</span></button></div>
        </div>
        <div class="features"><div><b>01</b><span><strong>多格式理解</strong><small>从不同资料中抓住重点</small></span></div><div><b>02</b><span><strong>多智能体协作</strong><small>策划、写作、设计、审校</small></span></div><div><b>03</b><span><strong>原生可编辑</strong><small>文本和图形均可二次编辑</small></span></div></div>
      </section>

      <section v-else-if="stage==='outline'" class="workspace">
        <div class="workspace-head"><div><div class="eyebrow"><span></span> STORYBOARD</div><h2>先确认故事，再生成页面</h2><p>大纲中的标题、顺序和页面意图都可以修改。</p></div><button class="primary" :disabled="busy" @click="generate">并发生成全部页面 <span>→</span></button></div>
        <div class="outline-title"><label>演示标题</label><input v-model="project!.title"></div>
        <div class="outline-list">
          <div v-for="(slide,index) in project!.slides" :key="index" class="outline-row"><span class="drag">⋮⋮</span><span class="number">{{ String(index+1).padStart(2,'0') }}</span><div class="outline-fields"><input v-model="slide.title"><input v-model="slide.subtitle" class="sub"></div><select v-model="slide.layout"><option value="cover">封面</option><option value="content">内容</option><option value="quote">金句</option><option value="summary">总结</option></select><button class="delete" @click="removeSlide(index)">×</button></div>
        </div><button class="add" @click="addSlide">＋ 添加一页</button>
      </section>

      <section v-else-if="stage==='generating'" class="generating"><div class="orb"><span>{{ project!.progress }}%</span></div><h2>智能体正在并发创作</h2><p>内容智能体与设计智能体正在逐页工作，审校智能体会在交付前检查表达。</p><div class="progress"><i :style="{width:project!.progress+'%'}"></i></div><small>{{ project!.slides.filter(s=>s.generated).length }} / {{ project!.slides.length }} 页已完成</small></section>

      <section v-else class="result workspace">
        <div class="workspace-head"><div><div class="eyebrow"><span></span> DECK READY</div><h2>{{ project!.title }}</h2><p>{{ project!.slides.length }} 页内容已生成，下载后可在 PowerPoint 中自由编辑。</p></div><div class="actions"><button class="secondary" @click="project!.status='OUTLINE_READY'">返回大纲</button><button class="primary" :disabled="project!.status!=='COMPLETED'" @click="download">下载 PPTX ↓</button></div></div>
        <div v-if="project!.status==='FAILED'" class="error">生成失败：{{ project!.errorMessage }}</div>
        <div class="deck-editor"><div class="thumbs"><button v-for="(s,i) in project!.slides" :key="i" :class="{active:i===activeSlide}" @click="activeSlide=i"><span>{{ i+1 }}</span><div><b>{{ s.title }}</b><small>{{ s.layout }}</small></div></button></div><div class="slide-canvas" :class="project!.slides[activeSlide].layout"><div class="page-no">{{ String(activeSlide+1).padStart(2,'0') }}</div><h3>{{ project!.slides[activeSlide].title }}</h3><i></i><h4>{{ project!.slides[activeSlide].subtitle }}</h4><ul><li v-for="line in (project!.slides[activeSlide].bullets||'').split('\n').filter(Boolean)" :key="line">{{ line }}</li></ul><small class="deck-name">{{ project!.title }}</small></div></div>
      </section>
      <div v-if="error" class="toast" @click="error=''">{{ error }} <span>×</span></div>
    </main>
  </div>
</template>
