(function(){
  'use strict';
  const canvas=document.getElementById('flat');
  let style=null,accent='#4F7DFF',playing=false,animation=1,visible=false,requestedVisible=true,typography='modern',artworkShape='rounded',initialized=false;
  let title='',artist='',artData='',artImage=null;
  let ui={artwork:true,title:true,artist:true,progress:true,waves:true,shuffle:true,repeat:true,modeText:true};
  let positionMs=0,durationMs=0,raf=0,last=0,lastInteraction=0,pageVisible=!document.hidden,lastSpectrumPull=0;
  const spectrum=new Float32Array(24), peaks=new Float32Array(24);

  function canRender(){return initialized&&visible&&pageVisible;}
  function markInteraction(){lastInteraction=performance.now();}
  function resize(){
    const dpr=Math.min(window.devicePixelRatio||1,2);
    const w=Math.max(1,Math.floor(canvas.clientWidth*dpr));
    const h=Math.max(1,Math.floor(canvas.clientHeight*dpr));
    if(canvas.width!==w||canvas.height!==h){canvas.width=w;canvas.height=h;}
  }
  function notifyReady(){
    try{if(window.AndroidRenderer&&typeof window.AndroidRenderer.onReady==='function')window.AndroidRenderer.onReady(style||'');}catch(_e){}
  }
  function updateSpectrum(ts){
    if(style!=='equalizer2d'||!playing||ts-lastSpectrumPull<34)return;
    lastSpectrumPull=ts;
    let raw='';
    try{if(window.AndroidSpectrum&&typeof window.AndroidSpectrum.getBands==='function')raw=window.AndroidSpectrum.getBands()||'';}catch(_e){}
    const parts=raw?raw.split(','):[];
    for(let i=0;i<spectrum.length;i++){
      const target=parts.length>i?Math.max(0,Math.min(1,(Number(parts[i])||0)/1000)):0;
      spectrum[i]=target>spectrum[i]?spectrum[i]+(target-spectrum[i])*.70:spectrum[i]+(target-spectrum[i])*.13;
      if(spectrum[i]>=peaks[i])peaks[i]=spectrum[i];else peaks[i]=Math.max(spectrum[i],peaks[i]-.015);
    }
  }
  function render(ts){
    if(!canRender()||!style)return;
    resize();
    updateSpectrum(Number(ts)||performance.now());
    const progress=durationMs>0?Math.max(0,Math.min(1,positionMs/durationMs)):0;
    PlayerModels.drawSkin2D(canvas,style,{accent,title,artist,artImage,artworkShape,playing,animation,immersive:style==='spotify2d',progress,positionMs,durationMs,tick:ts||Date.now(),typography,spectrum,peaks,ui});
  }
  function styleNeedsMotion(){
    if(!playing)return false;
    if(style==='equalizer2d')return true;
    if(animation<=0)return false;
    return style==='neon'||(style==='spotify2d'&&ui.waves!==false);
  }
  function shouldAnimate(ts){return canRender()&&(styleNeedsMotion()||(ts-lastInteraction<1400));}
  function loop(ts){
    raf=0;if(!canRender())return;
    const recent=ts-lastInteraction<1400;
    const interval=recent?16.7:(styleNeedsMotion()?50:100);
    if(ts-last>=interval){render(ts);last=ts;}
    if(shouldAnimate(ts))schedule();
  }
  function schedule(){if(!raf&&canRender())raf=requestAnimationFrame(loop);}
  function loadArt(done){
    if(!artData){artImage=null;if(done)done();return;}
    const img=new Image();
    img.onload=()=>{artImage=img;if(done)done();};
    img.onerror=()=>{artImage=null;if(done)done();};
    img.src=artData;
  }
  function finishInitialRender(){initialized=true;visible=requestedVisible;render();schedule();notifyReady();}
  function prepare(s,a,anim,typ,play,t,ar,d,shape){
    style=s||'classic';accent=typeof a==='string'?a:'#4F7DFF';animation=Math.max(0,Number(anim)||0);
    typography=typ||'modern';artworkShape=(shape==='circle'||shape==='square')?shape:'rounded';playing=!!play;title=t||'';artist=ar||'';artData=d||'';
    initialized=false;visible=false;
    if(raf){cancelAnimationFrame(raf);raf=0;}
    loadArt(finishInitialRender);
  }
  function send(cmd,extra){
    let url='app://control?cmd='+encodeURIComponent(cmd);
    if(extra&&extra.ratio!=null)url+='&ratio='+encodeURIComponent(Math.max(0,Math.min(1,extra.ratio)));
    location.href=url;
  }
  let longPressTimer=0,longPressFired=false;
  function normalized(e){const r=canvas.getBoundingClientRect();return {x:(e.clientX-r.left)/Math.max(1,r.width),y:(e.clientY-r.top)/Math.max(1,r.height)};}
  function editableZone(p){return style==='spotify2d'&&p.x>.08&&p.x<.92&&p.y>.10&&p.y<.68;}
  canvas.addEventListener('pointerdown',e=>{
    if(!initialized)return;markInteraction();schedule();longPressFired=false;const p=normalized(e);
    if(editableZone(p)){longPressTimer=setTimeout(()=>{longPressFired=true;send('edit');},650);}
  });
  canvas.addEventListener('pointermove',()=>{markInteraction();schedule();if(longPressTimer){clearTimeout(longPressTimer);longPressTimer=0;}});
  canvas.addEventListener('pointerup',e=>{
    if(!initialized)return;markInteraction();schedule();if(longPressTimer){clearTimeout(longPressTimer);longPressTimer=0;}
    if(longPressFired)return;
    const p=normalized(e);const action=PlayerModels.get2DControlAt(style,p.x,p.y);
    if(action&&((action.cmd!=='shuffle'||ui.shuffle)&&(action.cmd!=='repeat'||ui.repeat)&&(action.cmd!=='seek'||ui.progress)))send(action.cmd,action);
  });
  canvas.addEventListener('pointercancel',()=>{markInteraction();schedule();if(longPressTimer){clearTimeout(longPressTimer);longPressTimer=0;}});
  window.addEventListener('resize',()=>{render();schedule();});
  document.addEventListener('visibilitychange',()=>{pageVisible=!document.hidden;if(pageVisible){render();schedule();}else if(raf){cancelAnimationFrame(raf);raf=0;}});

  window.Player2D={
    prepare,
    setSkin(v){style=v||'classic';if(initialized){render();schedule();}},
    setAccent(v){accent=typeof v==='string'?v:'#4F7DFF';if(initialized)render();},
    setPlaying(v){playing=!!v;render();schedule();},
    setAnimation(v){animation=Math.max(0,Number(v)||0);render();schedule();},
    setTypography(v){typography=v||'modern';if(initialized)render();},
    setArtworkShape(v){artworkShape=(v==='circle'||v==='square')?v:'rounded';if(initialized)render();},
    setMetadata(t,a){title=t||'';artist=a||'';render();},
    setArtworkDataUrl(v){artData=v||'';loadArt(()=>{if(!initialized)finishInitialRender();else{render();notifyReady();}});},
    setTrack(t,a,d){title=t||'';artist=a||'';artData=d||'';loadArt(()=>{if(!initialized)finishInitialRender();else{render();notifyReady();}});},
    setProgress(p,d){positionMs=Math.max(0,Number(p)||0);durationMs=Math.max(0,Number(d)||0);if(style==='spotify2d')render();},
    setVisibility(artwork,titleVisible,artistVisible,progressVisible,wavesVisible,shuffleVisible,repeatVisible,modeTextVisible){
      ui.artwork=!!artwork;ui.title=!!titleVisible;ui.artist=!!artistVisible;ui.progress=!!progressVisible;ui.waves=!!wavesVisible;
      ui.shuffle=!!shuffleVisible;ui.repeat=!!repeatVisible;ui.modeText=!!modeTextVisible;render();
    },
    setAppVisible(v){requestedVisible=!!v;visible=requestedVisible;if(visible&&initialized){render();schedule();}else if(raf){cancelAnimationFrame(raf);raf=0;}},
    dispose(){requestedVisible=false;visible=false;initialized=false;if(raf)cancelAnimationFrame(raf);raf=0;if(longPressTimer){clearTimeout(longPressTimer);longPressTimer=0;}artImage=null;spectrum.fill(0);peaks.fill(0);}
  };
})();
