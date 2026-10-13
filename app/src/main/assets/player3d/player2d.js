(function(){
  'use strict';
  const canvas=document.getElementById('flat');
  let style=null,accent='#4F7DFF',playing=false,animation=1,visible=false,requestedVisible=true,typography='modern',artworkShape='rounded',initialized=false;
  let title='',artist='',artData='',artImage=null;
  let shuffle=false,repeatMode=0,favorite=false,favoriteVisible=false,queueVisible=false,backgroundColor='#385044';
  let neonMode='accent',neonColors=['#4F7DFF','#4F7DFF','#4F7DFF'];
  let ui={artwork:true,title:true,artist:true,progress:true,waves:true,shuffle:true,repeat:true,modeText:true};
  let positionMs=0,durationMs=0,raf=0,last=0,lastInteraction=0,pageVisible=!document.hidden,lastSpectrumPull=0;
  let xpTopCss=0,xpBottomCss=0,xpRadio=false,xpRadioState=0,xpHasMedia=true,xpPressed='',xpSeekPreview=null;
  let xpLastPaint=0,xpFramePending=false,phoneLastPaint=0,phoneAlbum='',phonePosition=0,phoneTotal=0,phoneLyricsVisible=false;
  let xpTheme='blue',xpDesktopColor='#386da4',xpTitle='Reproductor de medios',xpPixelated=true,xpDisplay='cover';
  let phoneTheme='sky',phoneTitle='Reproductor',phonePixelated=true,phoneLcd=true,phoneCard='cover',phoneSaver=false;
  let artworkSwipeOffset=0,artworkSwipeDirection=0,artworkSwipePhase='',artworkSwipeStart=0,artworkSwipeDuration=180;
  const spectrum=new Float32Array(24), peaks=new Float32Array(24);

  function canRender(){return initialized&&visible&&pageVisible;}
  function markInteraction(){lastInteraction=performance.now();}
  function resize(){
    const dpr=style==='phone2d'?(phonePixelated?300/Math.max(1,canvas.clientWidth):Math.min(Math.max(window.devicePixelRatio||1,1),2)):style==='xp2d'?(xpPixelated?270:Math.min(Math.max(window.devicePixelRatio||1,1),2)*canvas.clientWidth)/Math.max(1,canvas.clientWidth):Math.min(window.devicePixelRatio||1,2);
    const w=style==='phone2d'&&phonePixelated?300:style==='xp2d'&&xpPixelated?270:Math.max(1,Math.floor(canvas.clientWidth*dpr));
    const h=Math.max(1,Math.round(canvas.clientHeight*dpr));
    if(canvas.width!==w||canvas.height!==h){canvas.width=w;canvas.height=h;}
  }
  function notifyReady(){
    try{if(window.AndroidRenderer&&typeof window.AndroidRenderer.onReady==='function')window.AndroidRenderer.onReady(style||'');}catch(_e){}
  }
  function updateSpectrum(ts){
    if(!((style==='equalizer2d')||(style==='xp2d'&&xpDisplay==='bars')
      ||(style==='phone2d'&&phoneCard==='bars'&&!phoneLyricsVisible&&!phoneSaver))||!playing||ts-lastSpectrumPull<34)return;
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
  function xpInsetValues(){const ratio=270/Math.max(1,canvas.clientWidth);return {xpTopInset:xpTopCss*ratio,xpBottomInset:xpBottomCss*ratio};}
  function phoneInsetValues(){const ratio=300/Math.max(1,canvas.clientWidth);return {phoneTopInset:xpTopCss*ratio,phoneBottomInset:xpBottomCss*ratio};}
  function render(ts){
    if(!canRender()||!style)return;
    const now=performance.now();
    if((style==='xp2d'||style==='phone2d')&&now-(style==='phone2d'?phoneLastPaint:xpLastPaint)<34){xpFramePending=true;schedule();return;}
    if(style==='xp2d'){xpLastPaint=now;xpFramePending=false;}
    if(style==='phone2d'){phoneLastPaint=now;xpFramePending=false;}
    resize();
    updateArtworkSwipe(Number(ts)||performance.now());
    updateSpectrum(Number(ts)||performance.now());
    const progress=durationMs>0?Math.max(0,Math.min(1,positionMs/durationMs)):0;
    PlayerModels.drawSkin2D(canvas,style,{accent,title,artist,artImage,artworkShape,playing,animation,immersive:style==='spotify2d'||style==='xp2d'||style==='phone2d',progress,positionMs,durationMs,tick:ts||performance.now(),typography,spectrum,peaks,ui,shuffle,repeatMode,favorite,favoriteVisible,queueVisible,backgroundColor,artworkSwipeOffset,neonMode,neonColors,radio:xpRadio,radioState:xpRadioState,hasMedia:xpHasMedia,pressedCmd:xpPressed,seekPreview:xpSeekPreview,xpTheme,xpDesktopColor,xpTitle,xpPixelated,xpDisplay,...xpInsetValues(),...phoneInsetValues(),album:phoneAlbum,queuePosition:phonePosition,queueTotal:phoneTotal,lyricsVisible:phoneLyricsVisible,phoneTheme,phoneTitle,phoneLcd,phoneCard,phonePixelated});
  }
  function styleNeedsMotion(){
    if(!playing)return false;
    if(style==='phone2d')return !phoneSaver&&!phoneLyricsVisible&&((phoneCard==='bars'&&playing)||(!xpRadio&&phoneCard==='cover'&&animation>0&&PhoneSkin.needsMarquee(title,ui.artwork)));
    if(style==='xp2d')return (xpDisplay==='bars'&&playing)||(!xpRadio&&animation>0&&XpSkin.needsMarquee(title+' - '+artist));
    if(style==='equalizer2d')return true;
    if(animation<=0)return false;
    return style==='neon';
  }
  function shouldAnimate(ts){return canRender()&&(styleNeedsMotion()||(style!=='xp2d'&&style!=='phone2d'&&ts-lastInteraction<1400)||xpFramePending||artworkSwipePhase==='out'||artworkSwipePhase==='in');}
  function swipeEase(t){t=Math.max(0,Math.min(1,t));return 1-Math.pow(1-t,3);}
  function updateArtworkSwipe(ts){
    if(artworkSwipePhase!=='out'&&artworkSwipePhase!=='in')return;
    const p=Math.max(0,Math.min(1,(ts-artworkSwipeStart)/Math.max(1,artworkSwipeDuration)));
    const eased=swipeEase(p);
    if(artworkSwipePhase==='out'){
      artworkSwipeOffset=artworkSwipeDirection*canvas.width*eased;
      if(p>=1)artworkSwipePhase='waiting';
    }else{
      artworkSwipeOffset=-artworkSwipeDirection*canvas.width*(1-eased);
      if(p>=1){artworkSwipeOffset=0;artworkSwipeDirection=0;artworkSwipePhase='';}
    }
  }
  function beginIncomingArtworkSwipe(){
    if(artworkSwipePhase!=='waiting'||!artworkSwipeDirection)return;
    artworkSwipeOffset=-artworkSwipeDirection*canvas.width;
    artworkSwipePhase='in';artworkSwipeStart=performance.now();
  }
  function loop(ts){
    raf=0;if(!canRender())return;
    const recent=ts-lastInteraction<1400;
    const interval=(style==='xp2d'||style==='phone2d')?(styleNeedsMotion()?50:34):(recent?16.7:(styleNeedsMotion()?50:100));
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
  function finishInitialRender(){initialized=true;visible=requestedVisible;beginIncomingArtworkSwipe();render();schedule();schedulePhoneMinute();notifyReady();}
  function prepare(s,a,anim,typ,play,t,ar,d,shape){
    style=s||'classic';canvas.classList.toggle('xp',style==='xp2d'&&xpPixelated);canvas.classList.toggle('phone',style==='phone2d'&&phonePixelated);accent=typeof a==='string'?a:'#4F7DFF';animation=Math.max(0,Number(anim)||0);
    typography=typ||'modern';artworkShape=(shape==='circle'||shape==='square')?shape:'rounded';playing=!!play;title=t||'';artist=ar||'';artData=d||'';
    initialized=false;visible=false;xpPressed='';xpSeekPreview=null;xpLastPaint=0;phoneLastPaint=0;xpFramePending=false;
    if(raf){cancelAnimationFrame(raf);raf=0;}
    loadArt(finishInitialRender);
  }
  function send(cmd,extra){
    let url='app://control?cmd='+encodeURIComponent(cmd);
    if(extra&&extra.ratio!=null)url+='&ratio='+encodeURIComponent(Math.max(0,Math.min(1,extra.ratio)));
    location.href=url;
  }
  let longPressTimer=0,longPressFired=false,xpPointerId=null,phoneMinuteTimer=0;
  function schedulePhoneMinute(){if(phoneMinuteTimer){clearTimeout(phoneMinuteTimer);phoneMinuteTimer=0;}
    if(!canRender()||style!=='phone2d')return;
    phoneMinuteTimer=setTimeout(()=>{phoneMinuteTimer=0;if(canRender()&&style==='phone2d')render();schedulePhoneMinute();},60000-Date.now()%60000+40);
  }
  function normalized(e){const r=canvas.getBoundingClientRect();return {x:(e.clientX-r.left)/Math.max(1,r.width),y:(e.clientY-r.top)/Math.max(1,r.height)};}
  function xpHit(p){const r=canvas.getBoundingClientRect();return PlayerModels.get2DControlAt('xp2d',p.x,p.y,r.width/Math.max(1,r.height),{favoriteVisible,queueVisible,ui,radio:xpRadio,durationMs,...xpInsetValues()});}
  function phoneState(){return {favoriteVisible,queueVisible,ui,radio:xpRadio,durationMs,lyricsVisible:phoneLyricsVisible,...phoneInsetValues()};}
  function phoneHit(p){const r=canvas.getBoundingClientRect();return PlayerModels.get2DControlAt('phone2d',p.x,p.y,r.width/Math.max(1,r.height),phoneState());}
  function phoneRatio(p){const r=canvas.getBoundingClientRect();return PhoneSkin.seekRatio(p.x,p.y,r.width/Math.max(1,r.height),phoneState());}
  function editableZone(p){return style==='spotify2d'&&p.x>.08&&p.x<.92&&p.y>.10&&p.y<.68;}
  canvas.addEventListener('pointerdown',e=>{
    if(!initialized)return;
    markInteraction();longPressFired=false;const p=normalized(e);
    if(style==='phone2d'||style==='xp2d'){
      const hit=style==='phone2d'?phoneHit(p):xpHit(p);xpPressed=hit&&hit.cmd!=='disabled'?hit.cmd:'';
      xpSeekPreview=xpPressed==='seek'?Math.max(0,Math.min(1,hit.ratio)):null;
      xpPointerId=e.pointerId;
      try{canvas.setPointerCapture(e.pointerId);}catch(_e){}
      render();return;
    }
    schedule();
    if(editableZone(p)){longPressTimer=setTimeout(()=>{longPressFired=true;send('edit');},650);}
  });
  canvas.addEventListener('pointermove',e=>{
    if(style==='phone2d'||style==='xp2d'){
      if(xpPointerId===e.pointerId&&xpPressed==='seek'){
        // A drag can leave the narrow track vertically; retain the horizontal seek.
        const logicalX=normalized(e).x*270;
        xpSeekPreview=style==='phone2d'?phoneRatio(normalized(e)):Math.max(0,Math.min(1,(logicalX-20)/230));render();
      }
      return;
    }
    markInteraction();schedule();if(longPressTimer){clearTimeout(longPressTimer);longPressTimer=0;}
  });
  canvas.addEventListener('pointerup',e=>{
    if(!initialized)return;
    markInteraction();if(longPressTimer){clearTimeout(longPressTimer);longPressTimer=0;}
    if(style==='phone2d'||style==='xp2d'){
      if(xpPointerId!==e.pointerId)return;
      const original=xpPressed,hit=style==='phone2d'?phoneHit(normalized(e)):xpHit(normalized(e));
      const ratio=xpSeekPreview;
      xpPointerId=null;xpPressed='';xpSeekPreview=null;render();
      if(original==='seek'&&ratio!=null)send('seek',{ratio});
      else if(original&&original!=='disabled'&&hit&&hit.cmd===original)send(original);
      return;
    }
    schedule();if(longPressFired)return;
    const p=normalized(e);const r=canvas.getBoundingClientRect();const action=PlayerModels.get2DControlAt(style,p.x,p.y,r.width/Math.max(1,r.height),{favoriteVisible,queueVisible});
    if(action&&((action.cmd!=='shuffle'||ui.shuffle)&&(action.cmd!=='repeat'||ui.repeat)&&(action.cmd!=='seek'||ui.progress)))send(action.cmd,action);
    else if(!action && p.y < (style==='spotify2d' ? .70 : .82)) send('lyrics');
  });
  canvas.addEventListener('pointercancel',()=>{
    if(style==='xp2d'||style==='phone2d'){xpPointerId=null;xpPressed='';xpSeekPreview=null;render();return;}
    markInteraction();schedule();if(longPressTimer){clearTimeout(longPressTimer);longPressTimer=0;}
  });
  window.addEventListener('resize',()=>{render();schedule();});
  document.addEventListener('visibilitychange',()=>{pageVisible=!document.hidden;if(pageVisible){render();schedule();schedulePhoneMinute();}else{if(phoneMinuteTimer){clearTimeout(phoneMinuteTimer);phoneMinuteTimer=0;}if(raf){cancelAnimationFrame(raf);raf=0;}}});

  window.addEventListener('phone-font-ready',()=>{if(style==='phone2d'&&initialized)render();});
  window.Player2D={
    prepare,
    setSkin(v){style=v||'classic';canvas.classList.toggle('xp',style==='xp2d'&&xpPixelated);canvas.classList.toggle('phone',style==='phone2d'&&phonePixelated);if(initialized){render();schedule();schedulePhoneMinute();}},
    setAccent(v){accent=typeof v==='string'?v:'#4F7DFF';if(initialized)render();},
    setPlaying(v){playing=!!v;render();schedule();},
    setAnimation(v){animation=Math.max(0,Number(v)||0);render();schedule();},
    setTypography(v){typography=v||'modern';if(initialized)render();},
    setArtworkShape(v){artworkShape=(v==='circle'||v==='square')?v:'rounded';if(initialized)render();},
    setMetadata(t,a){title=t||'';artist=a||'';render();},
    setArtworkDataUrl(v){artData=v||'';loadArt(()=>{if(!initialized)finishInitialRender();else{beginIncomingArtworkSwipe();render();schedule();notifyReady();}});},
    setTrack(t,a,d){title=t||'';artist=a||'';artData=d||'';loadArt(()=>{if(!initialized)finishInitialRender();else{beginIncomingArtworkSwipe();render();schedule();notifyReady();}});},
    setProgress(p,d){positionMs=Math.max(0,Number(p)||0);durationMs=Math.max(0,Number(d)||0);if(style==='spotify2d'||style==='xp2d'||style==='phone2d')render();},
    setPlaybackModes(s,r){shuffle=!!s;repeatMode=Math.max(0,Math.min(2,Number(r)||0));if(initialized)render();},
    setFavorite(v,visibleFlag){favorite=!!v;favoriteVisible=!!visibleFlag;if(initialized)render();},
    setQueueVisible(v){queueVisible=!!v;if(initialized)render();},
    setXpOptions(theme,desktop,windowTitle,pixelated,display){
      xpTheme=theme==='olive'||theme==='silver'?theme:'blue';
      xpDesktopColor=typeof desktop==='string'&&/^#[0-9a-fA-F]{6}$/.test(desktop)?desktop:'#386da4';
      xpTitle=typeof windowTitle==='string'&&windowTitle.trim()?windowTitle.trim():'Reproductor de medios';
      xpPixelated=pixelated!==false;xpDisplay=display==='bars'?'bars':'cover';
      if(style==='xp2d'){canvas.classList.toggle('xp',xpPixelated);resize();render();schedule();}
    },
    setPhoneOptions(theme,screenTitle,pixelated,lcd,card,saver){
      phoneTheme=theme==='green'||theme==='gray'?theme:'sky';
      phoneTitle=typeof screenTitle==='string'&&screenTitle.trim()?screenTitle.trim():'Reproductor';
      phonePixelated=pixelated!==false;phoneLcd=lcd!==false;phoneCard=card==='bars'?'bars':'cover';phoneSaver=saver===true;
      if(style==='phone2d'){canvas.classList.toggle('phone',phonePixelated);resize();render();schedule();}
    },
    setPhoneRadio(v,state,media){xpRadio=!!v;xpRadioState=Number(state)||0;xpHasMedia=!!media;if(initialized&&style==='phone2d')render();},
    setPhoneLyricsVisible(v){phoneLyricsVisible=!!v;if(initialized&&style==='phone2d'){render();schedule();}},
    setPhoneAlbum(v){phoneAlbum=String(v||'');if(initialized&&style==='phone2d')render();},
    setPhoneQueue(position,total){phonePosition=Number(position)||0;phoneTotal=Number(total)||0;if(initialized&&style==='phone2d')render();},
    setPhoneSystemInsets(top,bottom){xpTopCss=Math.max(0,Number(top)||0);xpBottomCss=Math.max(0,Number(bottom)||0);if(initialized&&style==='phone2d')render();},
    setXpRadio(v,state,media){xpRadio=!!v;xpRadioState=Number(state)||0;xpHasMedia=!!media;if(initialized&&style==='xp2d')render();},
    setXpSystemInsets(top,bottom){xpTopCss=Math.max(0,Number(top)||0);xpBottomCss=Math.max(0,Number(bottom)||0);if(initialized&&style==='xp2d')render();},
    setBackgroundColor(v){backgroundColor=typeof v==='string'?v:'#385044';if(initialized)render();},
    setNeonPalette(mode,c1,c2,c3){neonMode=mode||'accent';neonColors=[c1||accent,c2||c1||accent,c3||c2||c1||accent];if(initialized&&style==='neon')render();},
    setVisibility(artwork,titleVisible,artistVisible,progressVisible,wavesVisible,shuffleVisible,repeatVisible,modeTextVisible){
      ui.artwork=!!artwork;ui.title=!!titleVisible;ui.artist=!!artistVisible;ui.progress=!!progressVisible;ui.waves=!!wavesVisible;
      ui.shuffle=!!shuffleVisible;ui.repeat=!!repeatVisible;ui.modeText=!!modeTextVisible;render();
    },
    setAppVisible(v){requestedVisible=!!v;visible=requestedVisible;if(visible&&initialized){render();schedule();schedulePhoneMinute();}else{if(phoneMinuteTimer){clearTimeout(phoneMinuteTimer);phoneMinuteTimer=0;}if(raf){cancelAnimationFrame(raf);raf=0;}}},
    animateArtworkSwipe(direction,duration){
      if(!initialized)return;
      artworkSwipeDirection=direction==='left'?-1:1;
      artworkSwipeDuration=Math.max(1,Number(duration)||180);
      artworkSwipeOffset=0;artworkSwipePhase='out';artworkSwipeStart=performance.now();
      markInteraction();render();schedule();
    },
    dispose(){requestedVisible=false;visible=false;initialized=false;if(raf)cancelAnimationFrame(raf);raf=0;if(phoneMinuteTimer){clearTimeout(phoneMinuteTimer);phoneMinuteTimer=0;}if(longPressTimer){clearTimeout(longPressTimer);longPressTimer=0;}xpPointerId=null;xpPressed='';xpSeekPreview=null;xpFramePending=false;artImage=null;artworkSwipeOffset=0;artworkSwipeDirection=0;artworkSwipePhase='';spectrum.fill(0);peaks.fill(0);}
  };
})();
