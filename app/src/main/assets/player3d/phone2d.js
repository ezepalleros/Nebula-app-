/* Teléfono clásico: LCD de pantalla completa y mandos retro, sin carcasa exterior.
   Coordenadas compartidas por dibujo, toques y límites del visor Letras. */
(function(global){
  'use strict';
  const W=300;
  let fontReady=false;
  if(typeof FontFace==='function'){
    try{
      const face=new FontFace('PhoneCondensed','url("file:///android_asset/player3d/DejaVuSansCondensed.ttf")');
      face.load().then(loaded=>{
        document.fonts.add(loaded);fontReady=true;
        window.dispatchEvent(new Event('phone-font-ready'));
      }).catch(()=>{});
    }catch(_e){}
  }
  // El canvas se dibuja a 300 px lógicos de ancho y ocupa TODO el alto útil.
  // Los insets llegan desde NowPlayingFragment y se miden en estas unidades.
  function geometry(w,h,topInset,bottomInset){
    const unit=w/W,logicalH=h/unit;
    const top=Math.max(0,Number(topInset)||0)+2;
    const bottom=Math.max(top+300,logicalH-Math.max(0,Number(bottomInset)||0)-2);
    return {unit,logicalH,top,bottom,height:bottom-top,x:0,y:top,s:1};
  }
  function layout(h){
    const lcdBottom=h-118;
    const softTop=lcdBottom-27;
    const contentStart=55;
    const contentHeight=Math.max(110,softTop-contentStart);
    const cardY=contentStart+Math.min(12,contentHeight*.035);
    const cardH=Math.max(63,contentHeight*.46);
    const controlsY=contentStart+contentHeight*.65;
    const seekY=contentStart+contentHeight*.76;
    const seekH=Math.max(27,contentHeight*.185);
    const compact=contentHeight<225;
    return {h,lcdBottom,softTop,contentStart,contentHeight,cardY,cardH,controlsY,seekY,seekH,compact};
  }
  function rr(c,x,y,w,h,r,fill,stroke){
    const rad=Math.max(0,Math.min(r,w/2,h/2));c.beginPath();c.moveTo(x+rad,y);
    c.arcTo(x+w,y,x+w,y+h,rad);c.arcTo(x+w,y+h,x,y+h,rad);
    c.arcTo(x,y+h,x,y,rad);c.arcTo(x,y,x+w,y,rad);c.closePath();
    if(fill){c.fillStyle=fill;c.fill();}if(stroke){c.strokeStyle=stroke;c.lineWidth=1;c.stroke();}
  }
  function rect(c,x,y,w,h,color){c.fillStyle=color;c.fillRect(x,y,w,h);}
  function ellipse(c,x,y,rx,ry,color,edge){
    c.beginPath();c.ellipse(x,y,Math.max(.1,rx),Math.max(.1,ry),0,0,Math.PI*2);
    c.fillStyle=color;c.fill();if(edge){c.strokeStyle=edge;c.lineWidth=1;c.stroke();}
  }
  function font(size,bold){return (bold?'bold ':'')+size+'px '+(fontReady?'PhoneCondensed, ':'')+'sans-serif';}
  function label(c,t,x,y,size,color,bold){c.font=font(size,bold);c.textBaseline='alphabetic';c.fillStyle=color;c.fillText(t,x,y);}
  function ellipsis(c,text,width){text=String(text||'');if(c.measureText(text).width<=width)return text;
    while(text.length&&c.measureText(text+'…').width>width)text=text.slice(0,-1);
    return text+'…';
  }
  function titleText(c,text,x,y,width,size,o){
    c.font=font(size,true);const full=String(text||'');
    c.save();c.beginPath();c.rect(x,y-size-2,width,size+7);c.clip();
    c.shadowColor='rgba(10,39,77,.45)';c.shadowOffsetY=1;c.shadowBlur=1;
    if(o.playing&&o.animation>0&&c.measureText(full).width>width){
      const cycle=c.measureText(full).width+34,dx=((Number(o.tick)||0)*.022)%cycle;
      label(c,full,x-dx,y,size,'#ffffff',true);
      label(c,full,x-dx+cycle,y,size,'#ffffff',true);
    }else label(c,ellipsis(c,full,width),x,y,size,'#ffffff',true);
    c.restore();
  }
  function icon(c,name,x,y,color,scale){
    c.save();c.translate(x,y);c.scale(scale||1,scale||1);
    c.fillStyle=color;c.strokeStyle=color;c.lineWidth=2;c.lineCap='round';c.lineJoin='round';
    if(name==='play'){c.beginPath();c.moveTo(-4,-6);c.lineTo(6,0);c.lineTo(-4,6);c.closePath();c.fill();}
    else if(name==='pause'){rect(c,-5,-6,3,12,color);rect(c,2,-6,3,12,color);}
    else if(name==='rewind'||name==='forward'){
      const dir=name==='rewind'?-1:1;
      for(const off of [-4,3]){c.beginPath();c.moveTo(off-dir*3,-5);c.lineTo(off+dir*3,0);c.lineTo(off-dir*3,5);c.closePath();c.fill();}
    }else if(name==='previous'||name==='next'){
      const dir=name==='previous'?-1:1;c.beginPath();c.moveTo(-dir*5,-5);c.lineTo(dir*3,0);c.lineTo(-dir*5,5);c.closePath();c.fill();rect(c,dir*5-1,-5,2,10,color);
    }else if(name==='shuffle'){ellipse(c,0,0,3,3,color);}
    else if(name==='repeat'){
      c.beginPath();c.arc(0,0,6,-2.2,2.55);c.stroke();c.beginPath();c.moveTo(-4,-5);c.lineTo(-8,-2);c.lineTo(-3,0);c.fill();
    }else if(name==='stop'){rr(c,-4,-4,8,8,1,color);}
    c.restore();
  }
  function key(c,x,y,rx,ry,face,name,pressed,active){
    ellipse(c,x,y+1,rx+1,ry+1,'#5b7990');
    ellipse(c,x,y,rx,ry,pressed?'#41637c':active?'#8fe5fb':face,pressed?'#36566a':'#e6fcff');
    ellipse(c,x,y-ry*.31,rx*.72,ry*.24,'rgba(255,255,255,.22)');
    icon(c,name,x+(pressed?1:0),y+(pressed?1:0),active?'#105380':'#f6ffff');
  }
  function mmss(ms){const sec=Math.max(0,Math.floor((Number(ms)||0)/1000));return String(Math.floor(sec/60)).padStart(2,'0')+':'+String(sec%60).padStart(2,'0');}
  function draw(canvas,o){
    const c=canvas.getContext('2d');if(!c)return;
    const g=geometry(canvas.width,canvas.height,o.phoneTopInset,o.phoneBottomInset);
    const L=layout(g.height),H=L.h;
    const theme=o.phoneTheme==='green'?'green':o.phoneTheme==='gray'?'gray':'sky';
    const palette=theme==='green' ? {
      top:'#164b37',mid:'#3a8b61',bottom:'#6ab68a',heading:'#205540',bar:'#174b36',
      card:'rgba(181,231,188,.47)',cardBorder:'#daf8d8',controls:'#bcf3c0',
      panel:'#e3eee1',seek:'#469c67',key:'#478d69',text:'#204e3b',barColor:'#d2ffd2'
    } : theme==='gray' ? {
      top:'#3c5268',mid:'#738da3',bottom:'#a2b7c6',heading:'#405c77',bar:'#3b536d',
      card:'rgba(212,230,236,.45)',cardBorder:'#edfaff',controls:'#d7eef2',
      panel:'#edf2f4',seek:'#719eae',key:'#617e99',text:'#354d64',barColor:'#eefbff'
    } : {
      top:'#273b91',mid:'#43b7e0',bottom:'#2c87b9',heading:'#273b91',bar:'#16367e',
      card:'rgba(157,215,252,.43)',cardBorder:'rgba(238,250,255,.55)',controls:'#d1f5ff',
      panel:'#e9eff1',seek:'#4e96ce',key:'#4d9dd2',text:'#2e507e',barColor:'#d8fcff'
    };
    c.save();c.imageSmoothingEnabled=o.phonePixelated===false;
    rect(c,0,0,canvas.width,canvas.height,'#344c66');
    c.scale(g.unit,g.unit);c.translate(0,g.top);
    // Pantalla LCD ampliada a todo el ancho, con una moldura de sólo 2 píxeles.
    const sky=c.createLinearGradient(0,0,0,L.lcdBottom);
    sky.addColorStop(0,palette.top);sky.addColorStop(.22,theme==='sky'?'#3473b8':palette.heading);
    sky.addColorStop(.60,palette.mid);sky.addColorStop(1,palette.bottom);
    rect(c,0,0,W,L.lcdBottom,sky);
    c.save();c.globalAlpha=.13;
    for(const [cx,cy,rx] of [[52,L.contentStart+L.contentHeight*.43,60],[110,L.contentStart+L.contentHeight*.48,55],[253,L.contentStart+L.contentHeight*.32,68]]){
      ellipse(c,cx,cy,rx,rx*.34,'#f5ffff');
    }c.restore();
    rect(c,0,0,W,26,theme==='sky'?'rgba(20,46,124,.85)':palette.bar);
    for(let i=0;i<4;i++)rect(c,13+i*7,19-i*4,4,4+i*4,'#e8f6ff');
    rr(c,54,7,30,15,2,'#a3f777','#d9ffb3');rect(c,85,11,4,6,'#daffa7');
    if(!o.radio&&o.favoriteVisible!==false){
      c.save();c.translate(112,14);c.beginPath();for(let i=0;i<10;i++){
        const a=i*Math.PI/5-Math.PI/2,r=i%2?5:10;
        if(i===0)c.moveTo(Math.cos(a)*r,Math.sin(a)*r);else c.lineTo(Math.cos(a)*r,Math.sin(a)*r);
      }c.closePath();c.fillStyle=o.favorite?'#fffa81':'rgba(255,255,255,.12)';c.fill();
      c.strokeStyle='#e7fcff';c.stroke();c.restore();
    }
    const now=o.previewMode?new Date(2026,0,1,17,54):new Date();
    label(c,String(now.getHours()).padStart(2,'0')+':'+String(now.getMinutes()).padStart(2,'0'),246,20,15,'#e9ffff',false);
    rect(c,0,26,W,29,theme==='sky'?'rgba(22,54,126,.45)':palette.heading);
    c.font=font(18,true);
    label(c,ellipsis(c,o.phoneTitle||'Reproductor',205),12,47,18,'#ffffff',true);
    if(!o.radio&&o.queueVisible!==false&&o.queueTotal>0){
      const counter=String(Math.max(1,o.queuePosition||1))+'/'+String(o.queueTotal);
      c.font=font(16,false);label(c,counter,287-c.measureText(counter).width,47,16,'#e9ffff',false);
    }
    // Con Letras abiertas no dibujar tarjeta ni controles bajo el visor translúcido.
    if (!o.lyricsVisible) {
    // Tarjeta grande; los textos recuperan todo el ancho cuando no hay portada.
    rr(c,10,L.cardY,280,L.cardH,17,palette.card,palette.cardBorder);
    const showBars=o.phoneCard==='bars';
    if(showBars){
      // EqualizerSpectrum se entrega desde Java; las barras quedan dentro de la tarjeta.
      const count=24,gap=2,innerW=256,barW=(innerW-(count-1)*gap)/count;
      const floor=L.cardY+L.cardH-12,avail=Math.max(14,L.cardH-28);
      for(let i=0;i<count;i++){
        const source=o.spectrum||[],sample=Number(source[i])||0;
        // Sólo en las miniaturas: mostrar una forma de onda representativa.
        const value=o.previewMode&&(!sample)?(.16+.49*Math.pow(Math.sin(i*.42+1.1),2)):Math.max(0,Math.min(1,sample));
        const height=Math.max(2,avail*value);
        rr(c,22+i*(barW+gap),floor-height,barW,height,1,palette.barColor);
      }
    }
    if(!showBars){
    const showCover=!o.ui||o.ui.artwork!==false;
    const coverSize=Math.min(L.cardH-17,L.compact?64:115);
    let tx=22,tw=255;
    if(showCover){
      const left=19,cy=L.cardY+(L.cardH-coverSize)/2;
      rr(c,left-2,cy-2,coverSize+4,coverSize+4,5,'#285a83','#e4f7ff');
      if(o.artImage){const a=o.artImage,aw=a.naturalWidth||a.width,ah=a.naturalHeight||a.height;
        if(aw&&ah){const k=Math.min(coverSize/aw,coverSize/ah),iw=aw*k,ih=ah*k;
          c.drawImage(a,left+(coverSize-iw)/2,cy+(coverSize-ih)/2,iw,ih);}
      }else icon(c,'play',left+coverSize/2,cy+coverSize/2,'#c3e8ff',1.5);
      tx=left+coverSize+12;tw=280-tx;
    }
    const textSize=L.compact?14:20,secondary=L.compact?11:15;
    const titleY=L.cardY+L.cardH*(L.compact?.34:.30);
    if(!o.ui||o.ui.title!==false)titleText(c,o.title||'Tu canción',tx,titleY,tw,textSize,o);
    c.font=font(secondary,false);
    label(c,ellipsis(c,o.album||'Álbum',tw),tx,L.cardY+L.cardH*.63,secondary,'#dcf5ff',false);
    if(!o.ui||o.ui.artist!==false){
      c.font=font(secondary,true);
      label(c,ellipsis(c,o.artist||'Artista',tw),tx,L.cardY+L.cardH*.85,secondary,'#ffffff',true);
    }
    } // Termina el contenido de portada y textos o el visualizador.
    // Play / Aleatorio / Repetición, con estado de presión.
    const p=L.controlsY,r=L.compact?14:22;
    ellipse(c,58,p,r,r,o.pressedCmd==='playpause'?'#78b7c7':palette.controls,'#efffff');
    icon(c,o.playing?'pause':'play',58,p,'#218a58',L.compact?1:1.4);
    if(!o.radio&&(!o.ui||o.ui.shuffle!==false)){
      ellipse(c,150,p,L.compact?6:9,L.compact?6:9,o.shuffle?'#c5ff6a':'#7ca3c3','#e5f9ff');
      if(o.pressedCmd==='shuffle')ellipse(c,150,p,4,4,'#446a79');
    }
    if(!o.radio&&(!o.ui||o.ui.repeat!==false)){
      if(o.pressedCmd==='repeat')ellipse(c,244,p,17,17,'#6da5bb');
      icon(c,'repeat',244,p,o.repeatMode?'#d8ff78':'#e4f8ff',L.compact?1:1.55);
    }
    } // La portada y los controles superiores vuelven al cerrar Letras.
    // Panel de progreso extendido a casi todo el ancho.
    rr(c,10,L.seekY,280,L.seekH,12,palette.panel,'#ffffff');
    const trackY=L.seekY+(L.compact?6:12);
    rr(c,24,trackY,252,L.compact?7:11,5,'#becdda','#f8ffff');
    const seekAvailable=!o.radio&&Number(o.durationMs)>0&&(!o.ui||o.ui.progress!==false);
    if(seekAvailable){
      const t=Math.max(0,Math.min(1,Number(o.seekPreview==null?o.progress:o.seekPreview)||0));
      rr(c,26,trackY+2,Math.max(1,248*t),L.compact?3:6,2,palette.seek);
      ellipse(c,26+248*t,trackY+(L.compact?3.5:5.5),4,L.compact?5:8,'#e2f7ff','#668da7');
    }
    const timeY=L.seekY+L.seekH-(L.compact?5:10);
    if(o.radio)label(c,o.radioState===1?'Conectando…':'En vivo',106,timeY,L.compact?11:16,'#1b527b',true);
    else{
      label(c,mmss(o.positionMs),25,timeY,L.compact?11:16,'#2e507e',false);
      c.font=font(L.compact?11:16,false);
      const total=mmss(o.durationMs);
      label(c,total,274-c.measureText(total).width,timeY,L.compact?11:16,'#2e507e',false);
    }
    // Teclas blandas al final del LCD, siempre accesibles.
    rect(c,0,L.softTop,W,27,theme==='sky'?'rgba(13,47,110,.64)':palette.bar);
    label(c,'Opciones',12,L.softTop+20,18,'#ffffff',false);
    c.font=font(18,false);label(c,'Atrás',286-c.measureText('Atrás').width,L.softTop+20,18,'#ffffff',false);
    // Trama de la LCD: estática, sin animación propia.
    if(o.phoneLcd!==false){
      c.save();c.globalAlpha=.065;
      for(let yy=0;yy<L.lcdBottom;yy+=2)rect(c,0,yy,W,1,'#052054');
      c.restore();
    }
    rect(c,0,0,2,L.lcdBottom,'#193a68');rect(c,298,0,2,L.lcdBottom,'#193a68');
    // Teclado inferior INTEGRADO: sin cuerpo ni marco del celular.
    const base=c.createLinearGradient(0,L.lcdBottom,0,H);
    base.addColorStop(0,'#95afc7');base.addColorStop(.18,'#d2dde6');
    base.addColorStop(.72,'#bdcedb');base.addColorStop(1,'#94b0c4');
    rect(c,0,L.lcdBottom,W,H-L.lcdBottom,base);
    rect(c,0,L.lcdBottom,W,2,'#ebfcff');
    const keyTop=H-91,centerY=H-58,favoriteY=H-23;
    key(c,54,keyTop,37,11,palette.key,'options',o.pressedCmd==='options',false);
    label(c,'•••',41,keyTop+4,19,'#ffffff',true);
    key(c,246,keyTop,37,11,palette.key,'back',o.pressedCmd==='back',false);
    label(c,'◀',239,keyTop+5,17,'#ffffff',true);
    ellipse(c,150,centerY,48,46,'#708ca1','#f0f7fb');
    ellipse(c,150,centerY-1,44,42,'#eaf0f5','#ffffff');
    for(const [cmd,x,y,name,active] of [
      ['shuffle',150,centerY-29,'shuffle',o.shuffle],
      ['previous',117,centerY,'previous',false],
      ['next',183,centerY,'next',false],
      ['repeat',150,centerY+31,'repeat',o.repeatMode]
    ]){
      if(o.radio&&(cmd==='shuffle'||cmd==='repeat'))continue;
      if(o.pressedCmd===cmd)ellipse(c,x,y,12,11,'#abbecb');
      icon(c,name,x,y,active?'#348db6':'#698598',1.3);
    }
    rr(c,132,centerY-16,36,32,9,o.pressedCmd==='playpause'?'#819aaa':'#bdcdd8','#708ea4');
    icon(c,o.playing?'pause':'play',150,centerY,'#527a97',1.35);
    key(c,54,favoriteY,30,9,'#52c58c','favorite',o.pressedCmd==='favorite',o.favorite);
    key(c,246,favoriteY,30,9,'#dc686c','stop',o.pressedCmd==='stop',false);
    c.restore();
  }
  function coords(nx,ny,aspect,state){
    const logicalH=W/Math.max(.05,Number(aspect)||.5);
    const g=geometry(W,logicalH,state&&state.phoneTopInset,state&&state.phoneBottomInset);
    return {x:nx*W,y:ny*logicalH-g.top,H:g.height,L:layout(g.height)};
  }
  function seekRatio(nx,ny,aspect,state){const p=coords(nx,ny,aspect,state);return Math.max(0,Math.min(1,(p.x-26)/248));}
  function hit(nx,ny,aspect,state){
    const p=coords(nx,ny,aspect,state),x=p.x,y=p.y,H=p.H,L=p.L;
    const radio=!!(state&&state.radio),ui=state&&state.ui||{};
    function has(x1,y1,x2,y2){return x>=x1&&x<=x2&&y>=y1&&y<=y2;}
    if(x<0||x>W||y<0||y>H)return {cmd:'disabled'};
    if(has(96,0,133,27)&&!radio)return state&&state.favoriteVisible===false?{cmd:'disabled'}:{cmd:'favorite'};
    if(has(0,26,300,55))return radio||state&&state.queueVisible===false?{cmd:'disabled'}:{cmd:'queue'};
    if(has(0,L.softTop,135,L.lcdBottom))return {cmd:'options'};
    if(has(165,L.softTop,300,L.lcdBottom))return {cmd:'back'};
    // Bajo el visor de Letras no deben quedar botones invisibles activos.
    if (state && state.lyricsVisible && has(0,55,300,L.seekY)) {
      return radio ? {cmd:'disabled'} : {cmd:'lyrics'};
    }
    const controlTouch=L.compact?13:25;
    if(has(28,L.controlsY-controlTouch,86,L.controlsY+controlTouch))return {cmd:'playpause'};
    if(has(127,L.controlsY-controlTouch,175,L.controlsY+controlTouch))return radio||ui.shuffle===false?{cmd:'disabled'}:{cmd:'shuffle'};
    if(has(220,L.controlsY-controlTouch,269,L.controlsY+controlTouch))return radio||ui.repeat===false?{cmd:'disabled'}:{cmd:'repeat'};
    if(has(20,L.seekY,280,L.seekY+L.seekH))return radio||!state.durationMs||ui.progress===false?{cmd:'disabled'}:{cmd:'seek',ratio:seekRatio(nx,ny,aspect,state)};
    if(has(0,55,300,L.softTop))return radio?{cmd:'disabled'}:{cmd:'lyrics'};
    const keyTop=H-91,centerY=H-58,favY=H-23;
    if(has(12,keyTop-14,100,keyTop+14))return {cmd:'options'};
    if(has(200,keyTop-14,288,keyTop+14))return {cmd:'back'};
    if(has(99,centerY-46,201,centerY+45)){
      if(has(131,centerY-17,169,centerY+17))return {cmd:'playpause'};
      if(y<centerY-16)return radio||ui.shuffle===false?{cmd:'disabled'}:{cmd:'shuffle'};
      if(y>centerY+16)return radio||ui.repeat===false?{cmd:'disabled'}:{cmd:'repeat'};
      if(x<132)return {cmd:'previous'};
      if(x>168)return {cmd:'next'};
      return {cmd:'disabled'};
    }
    if(has(17,favY-14,92,favY+12))return radio||state&&state.favoriteVisible===false?{cmd:'disabled'}:{cmd:'favorite'};
    if(has(208,favY-14,282,favY+12))return {cmd:'stop'};
    return {cmd:'disabled'};
  }
  function lyricsRect(width,height,top,bottom){
    const g=geometry(width,height,top,bottom),L=layout(g.height);
    return {left:4*g.unit,top:(g.top+55)*g.unit,
      width:292*g.unit,height:Math.max(1,L.seekY-55-4)*g.unit};
  }
  const measureCanvas=document.createElement('canvas');
  global.PhoneSkin={draw,getControl:hit,seekRatio,geometry,lyricsRect,needsMarquee(t,cover){
    const c=measureCanvas.getContext('2d');if(!c)return false;
    c.font=font(20,true);
    return c.measureText(String(t||'')).width>(cover===false?255:146);
  }};
})(window);
