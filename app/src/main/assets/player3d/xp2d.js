/* Reproductor XP: arte y controles propios, sin recursos ni marcas de terceros. */
(function(global){
  'use strict';
  const W=270;
  const THEMES={
    blue:{frame:'#b7c9dd',edge:'#d8e8f7',shadow:'#617f9d',top:'#68b9ff',middle:'#2468d6',bottom:'#0d379b',button:'#c0dbec',buttonShadow:'#214681',ink:'#244d78'},
    olive:{frame:'#c4c7a5',edge:'#e6e8d2',shadow:'#7c825b',top:'#a8b97d',middle:'#687d32',bottom:'#394c20',button:'#d5dbaf',buttonShadow:'#5e7040',ink:'#3d4e2a'},
    silver:{frame:'#c4c9d6',edge:'#e9edf5',shadow:'#747c91',top:'#e4e8f5',middle:'#949fbd',bottom:'#5f6b91',button:'#e2e5ee',buttonShadow:'#616e8f',ink:'#424a65'}
  };
  const DEFAULT_DESKTOP='#386da4';
  function palette(name){return THEMES[name]||THEMES.blue;}
  function geometry(w,h,topInset,bottomInset){
    const u=w/W, H=h/u;
    const top=Math.max(14, (Number(topInset)||0)+5);
    const bottom=Math.max(top+240,H-(Number(bottomInset)||0)-10);
    const x=6, width=258;
    const screen={x:16,y:top+61,w:238,h:Math.max(80,bottom-(top+61)-102)};
    const statusY=screen.y+screen.h;
    return {u,H,top,bottom,x,width,screen,statusY,seekY:statusY+32,buttonY:statusY+69};
  }
  function rect(ctx,x,y,w,h,color){ctx.fillStyle=color;ctx.fillRect(Math.round(x),Math.round(y),Math.round(w),Math.round(h));}
  function border(ctx,x,y,w,h,light,dark,pressed){
    rect(ctx,x,y,w,h,pressed?dark:light);
    rect(ctx,x+1,y+1,w-2,h-2,pressed?'#b2bfce':'#edf5fd');
    rect(ctx,x+1,y+1,w-3,1,pressed?'#69809a':'#ffffff');
    rect(ctx,x+1,y+1,1,h-3,pressed?'#69809a':'#ffffff');
    rect(ctx,x+1,y+h-2,w-2,1,pressed?'#ffffff':dark);
    rect(ctx,x+w-2,y+1,1,h-2,pressed?'#ffffff':dark);
  }
  function label(ctx,text,x,y,color,size,bold){
    ctx.textBaseline='alphabetic';ctx.textAlign='left';ctx.fillStyle=color;
    ctx.font=(bold?'bold ':'')+(size||10)+'px sans-serif';ctx.fillText(text,x,y);
  }
  function clipped(ctx,text,x,y,width,scroll){
    ctx.save();ctx.beginPath();ctx.rect(x,y-11,width,14);ctx.clip();ctx.font='10px sans-serif';ctx.fillStyle='#d5e8f5';
    const measured=ctx.measureText(text).width;
    if(scroll&&measured>width){let offset=((scroll/85)%(measured+38));ctx.fillText(text,x-offset,y);ctx.fillText(text,x-offset+measured+38,y);} 
    else{let shortened=text;while(ctx.measureText(shortened).width>width&&shortened.length>1)shortened=shortened.slice(0,-2)+'…';ctx.fillText(shortened,x,y);}
    ctx.restore();
  }
  function rounded(ctx,x,y,w,h,r,fill){
    ctx.fillStyle=fill;ctx.beginPath();ctx.moveTo(x+r,y);ctx.arcTo(x+w,y,x+w,y+h,r);ctx.arcTo(x+w,y+h,x,y+h,r);ctx.arcTo(x,y+h,x,y,r);ctx.arcTo(x,y,x+w,y,r);ctx.closePath();ctx.fill();
  }
  function xpButton(ctx,x,y,size,id,pressed,active,enabled,large,theme){
    const radius=size/2;
    ctx.save();ctx.translate(x,y);
    const g=ctx.createLinearGradient(0,-radius,0,radius);
    g.addColorStop(0,pressed||active?theme.bottom:'#ffffff');g.addColorStop(.45,pressed||active?theme.middle:'#ecf5ff');g.addColorStop(1,pressed||active?theme.edge:theme.frame);
    ctx.fillStyle=theme.shadow;ctx.beginPath();ctx.arc(0,0,radius+1,0,Math.PI*2);ctx.fill();
    ctx.fillStyle=g;ctx.beginPath();ctx.arc(0,0,radius,0,Math.PI*2);ctx.fill();
    ctx.strokeStyle=pressed||active?theme.bottom:'#ffffff';ctx.lineWidth=1;ctx.beginPath();ctx.arc(-.5,-.5,radius-2,Math.PI*.9,Math.PI*1.9);ctx.stroke();
    if(pressed)ctx.translate(1,1);
    ctx.fillStyle=enabled===false?'#8ca0b0':theme.ink;ctx.strokeStyle=ctx.fillStyle;ctx.lineWidth=1.5;ctx.lineCap='round';
    const s=large?10:7;
    if(id==='playpause'){
      if(pressed=== 'pause' || active==='pause') {rect(ctx,-4,-6,3,12,ctx.fillStyle);rect(ctx,2,-6,3,12,ctx.fillStyle);}
      else{ctx.beginPath();ctx.moveTo(-3,-s*.7);ctx.lineTo(s*.65,0);ctx.lineTo(-3,s*.7);ctx.closePath();ctx.fill();}
    }else if(id==='stop'){rect(ctx,-4,-4,8,8,ctx.fillStyle);}
    else if(id==='previous'||id==='next'){
      ctx.beginPath();const dir=id==='previous'?-1:1;
      ctx.moveTo(-dir*4,-5);ctx.lineTo(dir*3,0);ctx.lineTo(-dir*4,5);ctx.closePath();ctx.fill();
      rect(ctx,dir*4-1,-5,2,10,ctx.fillStyle);
    }else if(id==='shuffle'){
      ctx.beginPath();ctx.moveTo(-6,-3);ctx.lineTo(-2,-3);ctx.lineTo(2,3);ctx.lineTo(6,3);ctx.moveTo(3,0);ctx.lineTo(6,3);ctx.lineTo(3,5);ctx.moveTo(-6,3);ctx.lineTo(-2,3);ctx.lineTo(2,-3);ctx.lineTo(6,-3);ctx.moveTo(3,-5);ctx.lineTo(6,-3);ctx.lineTo(3,0);ctx.stroke();
    }else if(id==='repeat'){
      ctx.beginPath();ctx.arc(0,0,5,-2.6,2.1);ctx.stroke();ctx.beginPath();ctx.moveTo(-2,-5);ctx.lineTo(-6,-3);ctx.lineTo(-3,0);ctx.closePath();ctx.fill();
    }
    ctx.restore();
  }
  function draw(canvas,opts){
    const ctx=canvas.getContext('2d');if(!ctx)return;
    const w=canvas.width,h=canvas.height;
    const m=geometry(w,h,opts.xpTopInset,opts.xpBottomInset);
    const theme=palette(opts.xpTheme);
    const ui=opts.ui||{};
    ctx.save();ctx.scale(m.u,m.u);ctx.imageSmoothingEnabled=opts.xpPixelated===false;
    rect(ctx,0,0,W,m.H,opts.xpDesktopColor||DEFAULT_DESKTOP);
    // Old-fashioned outer bezel and title strip.
    rounded(ctx,m.x,m.top,m.width,m.bottom-m.top,6,theme.shadow);
    rounded(ctx,m.x+1,m.top+1,m.width-2,m.bottom-m.top-2,5,theme.edge);
    rounded(ctx,m.x+2,m.top+2,m.width-4,m.bottom-m.top-4,4,theme.frame);
    const tg=ctx.createLinearGradient(0,m.top,0,m.top+25);
    tg.addColorStop(0,theme.top);tg.addColorStop(.35,theme.middle);tg.addColorStop(1,theme.bottom);
    rounded(ctx,m.x+3,m.top+3,m.width-6,24,4,tg);
    // Icono original: ventana con triángulo de reproducción, sin marcas.
    rounded(ctx,12,m.top+8,13,13,2,theme.edge);rect(ctx,14,m.top+10,9,9,theme.bottom);
    ctx.fillStyle='#b5ff75';ctx.beginPath();ctx.moveTo(17,m.top+12);ctx.lineTo(21,m.top+14.5);ctx.lineTo(17,m.top+17);ctx.closePath();ctx.fill();
    ctx.save();ctx.beginPath();ctx.rect(29,m.top+5,164,20);ctx.clip();
    ctx.font='bold 11px sans-serif';
    let caption=opts.xpTitle||'Reproductor de medios';
    while(ctx.measureText(caption).width>163&&caption.length>2)caption=caption.slice(0,-2)+'…';
    label(ctx,caption,29,m.top+19,opts.xpTheme==='silver'?'#253455':'#ffffff',11,true);
    ctx.restore();
    for(const [x,text,id,color] of [[197,'_',null,'#c0dbec'],[218,'□',null,'#c0dbec'],[239,'×','back','#f36747']]){
      rounded(ctx,x,m.top+7,18,16,3,theme.buttonShadow);rounded(ctx,x+1,m.top+8,16,14,2,id?'#f36747':theme.button);label(ctx,text,x+5,m.top+19,id?'#ffffff':theme.ink,11,true);
    }
    rect(ctx,9,m.top+28,252,26,'#eef2f8');
    rect(ctx,10,m.top+29,250,1,'#ffffff');rect(ctx,10,m.top+52,250,1,'#7893ad');
    label(ctx,'Archivo',17,m.top+45,'#111111',10,false);
    if(opts.queueVisible!==false)label(ctx,'Cola',66,m.top+45,opts.radio?'#929da9':'#111111',10,false);
    label(ctx,'Letras',101,m.top+45,opts.radio?'#929da9':'#111111',10,false);
    const favorite=opts.favorite?'SÍ':'NO';
    if(!opts.radio)label(ctx,'Favorita: '+favorite,178,m.top+45,opts.favorite?'#12588e':'#2b3748',9,false);
    // Black sunken image display; preserve image aspect ratio.
    const s=m.screen;border(ctx,s.x-2,s.y-2,s.w+4,s.h+4,'#3c5471','#ffffff',true);
    rect(ctx,s.x,s.y,s.w,s.h,'#030405');
    if(opts.xpDisplay==='bars'){
      const count=24, margin=9, gap=2, barWidth=(s.w-margin*2-gap*(count-1))/count;
      const levels=opts.spectrum||[];
      for(let i=0;i<count;i++){
        const live=Number(levels[i])||0;
        const level=opts.previewMode?(.15+.76*Math.abs(Math.sin(i*.56+1.2))):Math.max(0,Math.min(1,live));
        const segments=15, active=Math.ceil(level*segments);
        const blockH=Math.max(2,Math.floor((s.h-25)/segments)-1), step=Math.max(2,Math.floor((s.h-25)/segments));
        const x=s.x+margin+i*(barWidth+gap), base=s.y+s.h-12;
        for(let seg=0;seg<segments;seg++){
          const y=base-(seg+1)*step;
          if(y<s.y+4)break;
          rect(ctx,x,y,barWidth,blockH,seg<active?'#49cf39':'#16371b');
        }
      }
    }else if(opts.ui&&opts.ui.artwork!==false&&opts.artImage){
      const art=opts.artImage, aw=art.naturalWidth||art.width,ah=art.naturalHeight||art.height;
      if(aw>0&&ah>0){const scale=Math.min(s.w/aw,s.h/ah),iw=Math.max(1,Math.round(aw*scale)),ih=Math.max(1,Math.round(ah*scale));
        try{ctx.drawImage(art,s.x+Math.floor((s.w-iw)/2),s.y+Math.floor((s.h-ih)/2),iw,ih);}catch(e){}
      }
    }else if(opts.previewMode){
      rect(ctx,s.x+28,s.y+Math.max(5,Math.round(s.h*.13)),s.w-56,Math.min(s.h-15,133),'#f4c444');
      rounded(ctx,105,s.y+Math.round(s.h*.28),58,57,23,'#fcf7d1');
      rect(ctx,124,s.y+Math.round(s.h*.28)+56,20,36,'#fcf7d1');
    }
    // Status is flush against the display, not an independent card.
    rect(ctx,s.x,m.statusY,s.w,18,'#071018');rect(ctx,s.x,m.statusY,s.w,1,'#355675');
    rect(ctx,20,m.statusY+7,7,7,opts.radio&&opts.radioState===1?'#e1bb42':opts.playing?'#50d83c':'#6a9b60');
    const status=opts.radio?(opts.radioState===1?'Conectando…':opts.playing?'En vivo':'En pausa')
      :opts.playing?'Reproduciendo':opts.hasMedia===false?'Detenido':'En pausa';
    label(ctx,status,31,m.statusY+14,'#cbd9ee',9,false);
    const current=opts.positionMs||0,duration=opts.durationMs||0;
    function ft(ms){const sec=Math.max(0,Math.floor(ms/1000));return String(Math.floor(sec/60)).padStart(2,'0')+':'+String(sec%60).padStart(2,'0');}
    const time=opts.radio?'EN VIVO':ft(current)+' / '+ft(duration);
    ctx.font='9px sans-serif';const timeWidth=ctx.measureText(time).width;
    label(ctx,time,s.x+s.w-timeWidth-5,m.statusY+14,'#d5e8f5',9,false);
    if(opts.playing && !opts.radio && (ui.title!==false || ui.artist!==false)){
      const song=[ui.title!==false?opts.title||'':'',ui.artist!==false?opts.artist||'':''].filter(Boolean).join(' - ');
      const prefix=ctx.measureText(status).width+33;
      const left=s.x+prefix+7,right=s.x+s.w-timeWidth-9;
      if(right>left+9)clipped(ctx,song,left,m.statusY+14,right-left,opts.animation>0?opts.tick:0);
    }
    // Sunken seek trough and green knob.
    if(ui.progress!==false){
      rect(ctx,14,m.seekY-2,242,15,theme.edge);
      border(ctx,18,m.seekY+2,234,5,'#ffffff','#6984a3',true);
      const seekDisabled=opts.radio||!duration;
      const ratio=seekDisabled?0:Math.max(0,Math.min(1,opts.seekPreview==null?opts.progress:opts.seekPreview));
      const thumbX=20+230*(Number(ratio)||0);
      rect(ctx,Math.max(20,thumbX-3),m.seekY-1,7,12,seekDisabled?'#929ba4':'#6acb52');
      rect(ctx,Math.max(20,thumbX-3),m.seekY-1,7,1,seekDisabled?'#d5dae0':'#b9f5a4');
    }
    // Raised playback controls, pressed state visibly inset.
    rect(ctx,10,m.buttonY-25,250,51,theme.frame);rect(ctx,10,m.buttonY-25,250,1,theme.edge);
    const pressed=opts.pressedCmd||'';
    const controls=[['playpause',38,29],['stop',78,22],['previous',114,22],['next',150,22],['shuffle',204,22],['repeat',240,22]];
    for(const [id,x,size] of controls){
      if((id==='shuffle'&&ui.shuffle===false)||(id==='repeat'&&ui.repeat===false))continue;
      const active=id==='shuffle'&&opts.shuffle||id==='repeat'&&!!opts.repeatMode;
      xpButton(ctx,x,m.buttonY,size,id,id===pressed, id==='playpause'&&opts.playing?'pause':active,opts.hasMedia!==false,id==='playpause',theme);
    }
    rect(ctx,12,m.bottom-6,246,1,theme.shadow);rect(ctx,12,m.bottom-5,246,1,theme.edge);
    ctx.restore();
  }
  function getControl(nx,ny,aspect,state){
    const logicalH=W/Math.max(.05,aspect||.5);
    const m=geometry(W,logicalH,state&&state.xpTopInset,state&&state.xpBottomInset);
    const x=nx*W,y=ny*logicalH;
    if(x>=237&&x<=259&&y>=m.top+5&&y<=m.top+25)return {cmd:'back'};
    if(y>=m.top+30&&y<=m.top+54){
      if(x>=11&&x<=62)return {cmd:'options'};
      if(x>=63&&x<=98)return state&&(state.radio||state.queueVisible===false)?{cmd:'disabled'}:{cmd:'queue'};
      if(x>=99&&x<=150)return state&&state.radio?{cmd:'disabled'}:{cmd:'lyrics'};
      if(x>=171&&x<=259)return state&&state.radio?{cmd:'disabled'}:{cmd:'favorite'};
    }
    if(x>=m.screen.x&&x<m.screen.x+m.screen.w&&y>=m.screen.y&&y<m.screen.y+m.screen.h)
      return state&&state.radio?{cmd:'disabled'}:{cmd:'lyrics'};
    if(x>=14&&x<=256&&y>=m.seekY-9&&y<=m.seekY+18)
      return state&&(state.radio||!state.durationMs||(state.ui&&state.ui.progress===false))?{cmd:'disabled'}:{cmd:'seek',ratio:(x-20)/230};
    if(Math.abs(y-m.buttonY)<=20){
      for(const [cmd,cx,rad] of [['playpause',38,18],['stop',78,15],['previous',114,15],['next',150,15],['shuffle',204,15],['repeat',240,15]]){
        if(Math.abs(x-cx)<=rad){
          if(state&&state.ui&&((cmd==='shuffle'&&state.ui.shuffle===false)
             ||(cmd==='repeat'&&state.ui.repeat===false)))return {cmd:'disabled'};
          return {cmd};
        }
      }
    }
    return {cmd:'disabled'};
  }
  global.XpSkin={draw,geometry,getControl,needsMarquee(text){return (text||'').length>26;}};
})(window);
