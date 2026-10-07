(function(){
  'use strict';
  const glCanvas=document.getElementById('gl');
  const flatCanvas=document.getElementById('flat');
  let renderer=null;
  let style=null,accent='#8B5CF6',playing=false,animation=1,visible=false,requestedVisible=true,previewMode=false,typography='modern';
  let title='',artist='',artData='',artImage=null;
  let model=null,labelCanvas=null,labelTexture=null,initialized=false;
  let rotY=0.55,rotX=-0.10,scale=1,autoVel=0.004,userVel=0,touching=false,lastX=0,lastT=0,trackKick=0;
  let raf=0,lastRender=0,lastUpdate=performance.now(),lastInteraction=0,pageVisible=!document.hidden;

  function colorHex(v){ if(typeof v==='string')return v; const s=(v>>>0).toString(16).padStart(6,'0');return '#'+s.slice(-6); }
  function is2D(){ return !!style&&PlayerModels.is2DStyle(style); }
  function markInteraction(){lastInteraction=performance.now();}
  function ensureRenderer(){ if(!renderer)renderer=new Mini3D.Renderer(glCanvas,{preserveDrawingBuffer:false}); return renderer; }
  function syncCanvasVisibility(){
    const twoD=is2D();
    glCanvas.style.display=twoD?'none':'block';
    flatCanvas.style.display=twoD?'block':'none';
  }
  function resizeFlat(){
    const dpr=Math.min(window.devicePixelRatio||1,2);
    const w=Math.max(1,Math.floor(flatCanvas.clientWidth*dpr));
    const h=Math.max(1,Math.floor(flatCanvas.clientHeight*dpr));
    if(flatCanvas.width!==w||flatCanvas.height!==h){ flatCanvas.width=w; flatCanvas.height=h; }
  }
  function notifyReady(){
    try{ if(window.AndroidRenderer&&typeof window.AndroidRenderer.onReady==='function')window.AndroidRenderer.onReady(style||''); }catch(_e){}
  }
  function rebuild(){
    if(!initialized||!style)return;
    syncCanvasVisibility();
    if(model){model.dispose();model=null;}
    labelTexture=null;
    if(is2D()){
      trackKick=.3;
      renderNow();schedule();
      return;
    }
    const r=ensureRenderer();
    labelCanvas=PlayerModels.makeLabelCanvas(style,title,artist,artImage,accent,typography);
    labelTexture=r.createTexture(labelCanvas);
    model=PlayerModels.buildModel(r,style,labelTexture);
    r.cameraZ=model&&model.cameraZ||8.4;
    scale=model&&model.scale||1;
    rotY=.62;rotX=-.08;trackKick=.42;
    renderNow();schedule();
  }
  function refreshLabel(){
    if(!initialized)return;
    if(is2D()){renderNow();schedule();return;}
    if(!model||!labelTexture||!renderer)return;
    labelCanvas=PlayerModels.makeLabelCanvas(style,title,artist,artImage,accent,typography);
    renderer.updateTexture(labelTexture,labelCanvas);
    trackKick=.52;renderNow();schedule();
  }
  function loadArt(done){
    if(!artData){artImage=null;if(done)done();return;}
    const img=new Image();
    img.onload=()=>{artImage=img;if(done)done();};
    img.onerror=()=>{artImage=null;if(done)done();};
    img.src=artData;
  }
  function finishInitialRender(){
    initialized=true;visible=requestedVisible;rebuild();renderNow();notifyReady();
  }
  function prepare(s,a,anim,typ,play,t,ar,d){
    style=s||'classic';accent=colorHex(a||'#8B5CF6');animation=Math.max(0,Number(anim)||0);
    typography=typ||'modern';playing=!!play;title=t||'';artist=ar||'';artData=d||'';
    initialized=false;visible=false;
    if(raf){cancelAnimationFrame(raf);raf=0;}
    loadArt(finishInitialRender);
  }

  function renderNow(){
    if(!canRender())return;
    if(is2D()){
      resizeFlat();
      const progress=.34+((Date.now()/1800)%1)*.42;
      PlayerModels.drawSkin2D(flatCanvas,style,{accent,title,artist,artImage,playing,animation,previewMode,progress,tick:Date.now(),typography});
      return;
    }
    if(!model||!renderer)return;
    const kick=trackKick>0?Math.sin(trackKick*Math.PI*3)*trackKick*.45:0;
    const root={pos:[0,0,0],rot:[rotX,rotY+kick,0],scale:[scale*(1+trackKick*.05),scale*(1+trackKick*.05),scale*(1+trackKick*.05)]};
    renderer.render(model,root);
  }
  function canRender(){return initialized&&visible&&pageVisible;}
  function needsContinuousFrames(ts){
    if(!canRender())return false;
    if(touching||ts-lastInteraction<1400)return true;
    if(playing&&animation>0)return true;
    if(trackKick>0.001||Math.abs(userVel)>0.00001)return true;
    return false;
  }
  function tick(ts){
    raf=0;if(!canRender())return;
    const recentInteraction=touching||ts-lastInteraction<1400;
    const targetInterval=recentInteraction?16.7:(playing&&animation>0?50:100);
    const dt=Math.min(50,ts-lastUpdate||16);lastUpdate=ts;
    if(ts-lastRender>=targetInterval){
      if(is2D())renderNow();
      else{
        const target=(playing?0.0042*animation:0);
        autoVel+=(target-autoVel)*Math.min(1,dt/420);
        if(!touching){rotY+=autoVel*dt+userVel*dt;userVel*=Math.pow(.94,dt/16.7);}
        if(trackKick>0)trackKick=Math.max(0,trackKick-dt/520);
        if(model&&model.spinDisc&&playing)rotY+=0.0018*dt;
        renderNow();
      }
      lastRender=ts;
    }
    if(needsContinuousFrames(ts))schedule();
  }
  function schedule(){if(!raf&&canRender())raf=requestAnimationFrame(tick);}

  glCanvas.addEventListener('pointerdown',e=>{if(!initialized||is2D()||previewMode)return;markInteraction();touching=true;lastX=e.clientX;lastT=performance.now();userVel=0;glCanvas.setPointerCapture(e.pointerId);schedule();});
  glCanvas.addEventListener('pointermove',e=>{if(!touching||is2D())return;markInteraction();const now=performance.now(),dx=e.clientX-lastX,dt=Math.max(1,now-lastT);const d=dx*0.012;rotY+=d;userVel=d/dt;lastX=e.clientX;lastT=now;renderNow();});
  function release(){markInteraction();touching=false;schedule();}
  glCanvas.addEventListener('pointerup',release);glCanvas.addEventListener('pointercancel',release);
  window.addEventListener('resize',()=>{renderNow();schedule();});
  document.addEventListener('visibilitychange',()=>{pageVisible=!document.hidden;if(pageVisible){lastUpdate=performance.now();renderNow();schedule();}else if(raf){cancelAnimationFrame(raf);raf=0;}});

  window.Player3D={
    prepare,
    setSkin(s){style=s||'classic';if(initialized)rebuild();},
    setAccent(a){accent=colorHex(a);if(initialized)rebuild();},
    setPlaying(v){playing=!!v;renderNow();schedule();},
    setAnimation(v){animation=Math.max(0,Number(v)||0);renderNow();schedule();},
    setTypography(v){typography=v||'modern';if(initialized)rebuild();},
    setMetadata(t,a){title=t||'';artist=a||'';refreshLabel();},
    setArtworkDataUrl(d){artData=d||'';loadArt(()=>{if(!initialized)finishInitialRender();else{refreshLabel();notifyReady();}});},
    setTrack(t,a,d){title=t||'';artist=a||'';artData=d||'';loadArt(()=>{if(!initialized)finishInitialRender();else{refreshLabel();notifyReady();}});},
    setAppVisible(v){requestedVisible=!!v;visible=requestedVisible;if(visible&&initialized){lastUpdate=performance.now();renderNow();schedule();}else if(raf){cancelAnimationFrame(raf);raf=0;}},
    setRotationY(v){if(initialized&&!is2D()){rotY=Number(v)||0;userVel=0;renderNow();}},
    setPreviewMode(v){previewMode=!!v;renderNow();schedule();},
    debugState(){return {style,rotY,rotX,playing,touching,userVel,autoVel,visible,previewMode,is2D:is2D(),initialized};},
    dispose(){requestedVisible=false;visible=false;initialized=false;if(raf)cancelAnimationFrame(raf);raf=0;if(model){model.dispose();model=null;}if(renderer){renderer.dispose();renderer=null;}labelTexture=null;artImage=null;}
  };
  syncCanvasVisibility();
})();
