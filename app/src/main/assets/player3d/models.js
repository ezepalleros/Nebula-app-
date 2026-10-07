(function(global){
  'use strict';
  const M=Mini3D;

  const STYLES_3D=['pixel','cassette','gameboy3d','walkman','retro','vinyl'];
  const STYLES_2D=['spotify2d','equalizer2d','ring','classic','neon','glass','minimal'];

  const PIXEL={
    A:['01110','10001','10001','11111','10001','10001','10001'],B:['11110','10001','10001','11110','10001','10001','11110'],C:['01111','10000','10000','10000','10000','10000','01111'],D:['11110','10001','10001','10001','10001','10001','11110'],E:['11111','10000','10000','11110','10000','10000','11111'],F:['11111','10000','10000','11110','10000','10000','10000'],G:['01111','10000','10000','10111','10001','10001','01110'],H:['10001','10001','10001','11111','10001','10001','10001'],I:['11111','00100','00100','00100','00100','00100','11111'],J:['00111','00010','00010','00010','10010','10010','01100'],K:['10001','10010','10100','11000','10100','10010','10001'],L:['10000','10000','10000','10000','10000','10000','11111'],M:['10001','11011','10101','10101','10001','10001','10001'],N:['10001','11001','10101','10011','10001','10001','10001'],O:['01110','10001','10001','10001','10001','10001','01110'],P:['11110','10001','10001','11110','10000','10000','10000'],Q:['01110','10001','10001','10001','10101','10010','01101'],R:['11110','10001','10001','11110','10100','10010','10001'],S:['01111','10000','10000','01110','00001','00001','11110'],T:['11111','00100','00100','00100','00100','00100','00100'],U:['10001','10001','10001','10001','10001','10001','01110'],V:['10001','10001','10001','10001','10001','01010','00100'],W:['10001','10001','10001','10101','10101','10101','01010'],X:['10001','10001','01010','00100','01010','10001','10001'],Y:['10001','10001','01010','00100','00100','00100','00100'],Z:['11111','00001','00010','00100','01000','10000','11111'],
    '0':['01110','10001','10011','10101','11001','10001','01110'],'1':['00100','01100','00100','00100','00100','00100','01110'],'2':['01110','10001','00001','00010','00100','01000','11111'],'3':['11110','00001','00001','01110','00001','00001','11110'],'4':['00010','00110','01010','10010','11111','00010','00010'],'5':['11111','10000','10000','11110','00001','00001','11110'],'6':['01110','10000','10000','11110','10001','10001','01110'],'7':['11111','00001','00010','00100','01000','01000','01000'],'8':['01110','10001','10001','01110','10001','10001','01110'],'9':['01110','10001','10001','01111','00001','00001','01110'],
    ' ':['00000','00000','00000','00000','00000','00000','00000'],'.':['00000','00000','00000','00000','00000','00110','00110'],"'":['00100','00100','00000','00000','00000','00000','00000'],'-':['00000','00000','00000','11111','00000','00000','00000'],'/':['00001','00010','00100','01000','10000','00000','00000'],'&':['01100','10010','10100','01000','10101','10010','01101']
  };

  function cleanText(s){ return (s||'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toUpperCase(); }
  function ellipsize(s,max){ s=cleanText(s); return s.length>max?s.slice(0,Math.max(1,max-3))+'...':s; }
  function drawPixelText(ctx,text,x,y,scale,color,maxChars){
    text=ellipsize(text,maxChars||24); ctx.fillStyle=color; let cx=x;
    for(const ch of text){ const g=PIXEL[ch]||PIXEL[' ']; for(let row=0;row<7;row++)for(let col=0;col<5;col++)if(g[row][col]==='1')ctx.fillRect(Math.round(cx+col*scale),Math.round(y+row*scale),Math.ceil(scale),Math.ceil(scale)); cx+=6*scale; }
  }
  function fitText(ctx,text,maxWidth){
    let out=text||''; if(!out)return '';
    while(ctx.measureText(out).width>maxWidth && out.length>1) out=out.slice(0,-2)+'…';
    return out;
  }
  function roundRect(ctx,x,y,w,h,r,fill,stroke){
    ctx.beginPath();
    ctx.moveTo(x+r,y); ctx.arcTo(x+w,y,x+w,y+h,r); ctx.arcTo(x+w,y+h,x,y+h,r); ctx.arcTo(x,y+h,x,y,r); ctx.arcTo(x,y,x+w,y,r); ctx.closePath();
    if(fill){ctx.fillStyle=fill;ctx.fill();}
    if(stroke){ctx.strokeStyle=stroke;ctx.stroke();}
  }
  function artworkRadius(shape,w,h,roundedRadius){
    if(shape==='circle') return Math.min(w,h)*.5;
    if(shape==='square') return 0;
    return Math.max(0,roundedRadius||18);
  }
  function drawArtwork(ctx,art,x,y,w,h,accent,r){
    const radius=Number.isFinite(r)?Math.max(0,Math.min(r,Math.min(w,h)*.5)):18;
    roundRect(ctx,x,y,w,h,radius,accent||'#7c4dff');
    if(art){ try{ctx.save(); ctx.beginPath(); ctx.moveTo(x+radius,y); ctx.arcTo(x+w,y,x+w,y+h,radius); ctx.arcTo(x+w,y+h,x,y+h,radius); ctx.arcTo(x,y+h,x,y,radius); ctx.arcTo(x,y,x+w,y,radius); ctx.closePath(); ctx.clip(); ctx.drawImage(art,x,y,w,h); ctx.restore(); return; }catch(e){} }
    ctx.fillStyle='rgba(255,255,255,.16)';
    for(let i=0;i<6;i++) ctx.fillRect(x+18+i*18,y+h*0.56-(i%3)*8,10,26+(i%4)*10);
  }
  function fontForStyle(style,bold,size,typography){
    const weight=bold?'700':'500';
    const families={modern:'sans-serif',rounded:'sans-serif-rounded',serif:'serif',mono:'monospace',condensed:'sans-serif-condensed'};
    let family=families[typography]||null;
    if(!family) family='sans-serif';
    return weight+' '+size+'px '+family;
  }

  function makeLabelCanvas(style,title,artist,artImage,accent,typography){
    const c=document.createElement('canvas');
    let w=512,h=512;
    if(style==='cassette') {w=768;h=300;}
    else if(style==='walkman'){w=640;h=420;}
    else if(style==='pixel'){w=520;h=620;}
    else if(style==='retro'){w=520;h=600;}
    else if(style==='gameboy3d'){w=520;h=480;}
    else if(style==='vinyl'){w=512;h=512;}
    c.width=w;c.height=h; const ctx=c.getContext('2d'); ctx.imageSmoothingEnabled=false;
    const bg=style==='pixel'?'#e9e9eb':style==='retro'?'#f5f3ed':style==='cassette'?'#f4f1e7':style==='gameboy3d'?'#7f914f':style==='walkman'?'#202733':'#10141d';
    ctx.fillStyle=bg;ctx.fillRect(0,0,w,h);
    let art={x:20,y:20,w:w-40,h:Math.floor(h*.62)}, textY=Math.floor(h*.70), titleColor='#ffffff', subColor='#c8cede';
    if(style==='cassette'){art={x:18,y:18,w:220,h:h-36};textY=82;titleColor='#2d2b27';subColor='#68645d';}
    else if(style==='walkman'){art={x:18,y:18,w:w-36,h:250};textY=310;titleColor='#ffffff';subColor='#d2d8e6';}
    else if(style==='pixel'){art={x:36,y:30,w:w-72,h:350};textY=430;titleColor='#24262b';subColor='#646972';}
    else if(style==='retro'){art={x:38,y:30,w:w-76,h:320};textY=400;titleColor='#2a2b30';subColor='#666b75';}
    else if(style==='gameboy3d'){art={x:28,y:24,w:w-56,h:280};textY=342;titleColor='#1d2817';subColor='#35472b';}
    else if(style==='vinyl'){art={x:0,y:0,w:w,h:h};textY=0;}
    if(artImage){ try{ctx.drawImage(artImage,art.x,art.y,art.w,art.h);}catch(e){} } else { ctx.fillStyle=accent||'#7c4dff';ctx.fillRect(art.x,art.y,art.w,art.h);ctx.fillStyle='#ffffff';for(let i=0;i<14;i++){ctx.fillRect(art.x+18+i*24,art.y+art.h/2-(i%4)*12,12,12+(i%5)*18);} }
    if(style!=='vinyl'){
      const textX=style==='cassette'?270:32;
      const textW=style==='cassette'?420:w-64;
      if((typography||'mono')==='mono'){
        const scale=Math.max(2,Math.floor(w/220));
        drawPixelText(ctx,title||'NOW PLAYING',textX,textY,scale,titleColor,style==='cassette'?22:20);
        drawPixelText(ctx,artist||'ARTIST',textX,textY+42,Math.max(2,scale-1),subColor,style==='cassette'?24:24);
      }else{
        ctx.fillStyle=titleColor;ctx.font=fontForStyle(style,true,Math.max(20,w*.050),typography);ctx.fillText(fitText(ctx,title||'Now Playing',textW),textX,textY+24);
        ctx.fillStyle=subColor;ctx.font=fontForStyle(style,false,Math.max(13,w*.032),typography);ctx.fillText(fitText(ctx,artist||'Artist',textW),textX,textY+58);
      }
      ctx.fillStyle=accent||'#7c4dff';ctx.fillRect(textX,textY+82,textW,8);
    }
    return c;
  }

  function drawEqualizer(ctx,x,y,w,h,accent,tick){
    const bars=14, gap=w/(bars*1.7); const bw=gap*.9;
    for(let i=0;i<bars;i++){
      const phase=(tick*.0026)+(i*.52);
      const bh=Math.max(6,h*(0.2+0.8*(0.5+0.5*Math.sin(phase))));
      ctx.fillStyle=i%3===0?accent:'rgba(255,255,255,.24)';
      roundRect(ctx,x+i*gap*1.2,y+h-bh,bw,bh,bw/2,ctx.fillStyle);
    }
  }

  function formatTime(ms){
    if(!Number.isFinite(ms)||ms<=0)return '0:00';
    const total=Math.floor(ms/1000),m=Math.floor(total/60),s=total%60;
    return m+':'+String(s).padStart(2,'0');
  }

  function drawSkin2D(canvas, style, opts){
    const ctx=canvas.getContext('2d'); if(!ctx)return;
    const w=canvas.width,h=canvas.height;
    ctx.clearRect(0,0,w,h); ctx.imageSmoothingEnabled=false;
    const accent=opts.accent||'#8B5CF6';
    const title=opts.title||'Tu canción';
    const artist=opts.artist||'Tu artista';
    const art=opts.artImage||null;
    const tick=opts.tick||0;
    const progress=Math.max(0,Math.min(1,opts.progress==null?.36:opts.progress));
    const playing=!!opts.playing;
    const typography=opts.typography||null;
    const immersive=!!opts.immersive;
    const currentTime=opts.currentTime||formatTime(opts.positionMs||0);
    const durationTime=opts.durationTime||formatTime(opts.durationMs||0);

    const ui=opts.ui||{artwork:true,title:true,artist:true,progress:true,waves:true,shuffle:true,repeat:true,modeText:true};

    if(style==='ring'){
      const bg='#050708';
      ctx.fillStyle=bg;ctx.fillRect(0,0,w,h);
      const cx=w*.5,cy=h*.47,coverR=Math.min(w,h)*.255;
      const baseR=coverR+Math.max(5,w*.018);
      const pulse=1+.018*(.5+.5*Math.sin((tick||900)*.006));

      ctx.save();ctx.imageSmoothingEnabled=true;
      ctx.beginPath();ctx.arc(cx,cy,coverR*pulse,0,Math.PI*2);ctx.clip();
      if(art){try{ctx.drawImage(art,cx-coverR*pulse,cy-coverR*pulse,coverR*pulse*2,coverR*pulse*2);}catch(e){}}
      else{
        const ag=ctx.createLinearGradient(cx-coverR,cy-coverR,cx+coverR,cy+coverR);
        ag.addColorStop(0,'#6d28d9');ag.addColorStop(.48,'#db2777');ag.addColorStop(1,'#f59e0b');
        ctx.fillStyle=ag;ctx.fillRect(cx-coverR,cy-coverR,coverR*2,coverR*2);
        ctx.globalAlpha=.28;ctx.fillStyle='#ffffff';
        ctx.beginPath();ctx.arc(cx-coverR*.20,cy-coverR*.12,coverR*.72,0,Math.PI*2);ctx.fill();
        ctx.globalAlpha=1;
        ctx.fillStyle='rgba(0,0,0,.28)';ctx.fillRect(cx-coverR,cy+coverR*.38,coverR*2,coverR*.62);
        ctx.fillStyle='#ffffff';ctx.textAlign='center';ctx.font='800 '+Math.max(11,w*.050)+'px sans-serif';
        ctx.fillText('TU MÚSICA',cx,cy+coverR*.68);ctx.textAlign='left';
      }
      ctx.restore();

      const rays=96,maxLen=Math.max(14,w*.12),minLen=Math.max(2,w*.010);
      ctx.lineCap='round';
      for(let pass=0;pass<2;pass++){
        ctx.strokeStyle=accent;ctx.globalAlpha=pass===0?.18:.94;ctx.lineWidth=pass===0?Math.max(5,w*.022):Math.max(1.6,w*.007);
        for(let i=0;i<rays;i++){
          const a=-Math.PI/2+Math.PI*2*i/rays;
          const mirrored=(Math.sin(a)+1)*.5;
          const synthetic=.18+.72*Math.abs(Math.sin(mirrored*8.2+(tick||900)*.0027))*(.55+.45*Math.abs(Math.sin(mirrored*17.1+1.3)));
          const len=minLen+maxLen*(.16+.84*synthetic);
          const c=Math.cos(a),sn=Math.sin(a);
          ctx.beginPath();ctx.moveTo(cx+c*baseR,cy+sn*baseR);ctx.lineTo(cx+c*(baseR+len),cy+sn*(baseR+len));ctx.stroke();
        }
      }
      ctx.globalAlpha=.55;ctx.strokeStyle=accent;ctx.lineWidth=Math.max(1.2,w*.005);ctx.beginPath();ctx.arc(cx,cy,baseR,0,Math.PI*2);ctx.stroke();ctx.globalAlpha=1;
      return;
    }

    if(style==='equalizer2d'){
      const bg=ctx.createLinearGradient(0,0,0,h);
      bg.addColorStop(0,'#08090d');bg.addColorStop(.58,'#030406');bg.addColorStop(1,'#000000');
      ctx.fillStyle=bg;ctx.fillRect(0,0,w,h);
      const glow=ctx.createRadialGradient(w*.50,h*.44,0,w*.50,h*.44,w*.72);
      glow.addColorStop(0,hexToRgba(accent,.13));glow.addColorStop(1,'rgba(0,0,0,0)');
      ctx.fillStyle=glow;ctx.fillRect(0,0,w,h);

      const values=(opts.spectrum&&opts.spectrum.length)?opts.spectrum:null;
      const peakValues=(opts.peaks&&opts.peaks.length)?opts.peaks:null;
      const count=24, left=w*.075, right=w*.925, top=h*.105, base=h*.64, reflectBottom=h*.91;
      const gap=w*.010, totalW=right-left, barW=(totalW-gap*(count-1))/count;
      const maxH=base-top, segments=15, segGap=Math.max(2,h*.006), segH=(maxH-segGap*(segments-1))/segments;
      function levelFor(i){
        if(values){const idx=Math.min(values.length-1,Math.floor(i*values.length/count));return Math.max(.035,Math.min(1,Number(values[idx])||0));}
        return .18+.70*Math.abs(Math.sin(i*.71+(tick||900)*.0019))*(.52+.48*Math.abs(Math.sin(i*.31+1.2)));
      }
      function peakFor(i,lvl){
        if(peakValues){const idx=Math.min(peakValues.length-1,Math.floor(i*peakValues.length/count));return Math.max(lvl,Math.min(1,Number(peakValues[idx])||0));}
        return Math.min(1,lvl+.08);
      }
      for(let i=0;i<count;i++){
        const lvl=levelFor(i), peak=peakFor(i,lvl), x=left+i*(barW+gap);
        const lit=Math.max(1,Math.round(lvl*segments));
        for(let sidx=0;sidx<segments;sidx++){
          const y=base-(sidx+1)*(segH+segGap)+segGap;
          const ratio=(sidx+1)/segments;
          let color=ratio>.77?'#ff2b18':ratio>.48?'#ffd31a':'#31d318';
          const active=sidx<lit;
          roundRect(ctx,x,y,barW,segH,Math.min(3,barW*.22),active?color:hexToRgba(color,.10));
          if(active){
            const ry=base+(base-y)*.36;
            if(ry<reflectBottom){ctx.save();ctx.globalAlpha=.12*(1-ratio*.35);roundRect(ctx,x,ry,barW,segH,Math.min(3,barW*.22),color);ctx.restore();}
          }
        }
        const py=base-Math.max(segH,peak*maxH)-segGap*2;
        const pr=Math.max(0,Math.min(1,peak));
        const peakColor=pr>.77?'#ff2b18':pr>.48?'#ffd31a':'#31d318';
        roundRect(ctx,x,py,barW,Math.max(4,segH*.82),Math.min(3,barW*.22),peakColor);
      }
      ctx.fillStyle='rgba(255,255,255,.72)';ctx.font=fontForStyle(style,true,Math.max(12,w*.038),typography);
      ctx.textAlign='center';ctx.fillText(fitText(ctx,title,w*.76),w*.5,h*.975);ctx.textAlign='left';
      return;
    }

    if(style==='spotify2d'){
      // True full-screen 2D skin: no inner card/frame.
      const g=ctx.createLinearGradient(0,0,0,h);
      g.addColorStop(0,'#183626'); g.addColorStop(.28,'#0b1710'); g.addColorStop(1,'#050706');
      ctx.fillStyle=g;ctx.fillRect(0,0,w,h);
      const glow=ctx.createRadialGradient(w*.18,h*.16,0,w*.18,h*.16,w*.95);
      glow.addColorStop(0,'rgba(30,215,96,.17)');glow.addColorStop(1,'rgba(30,215,96,0)');ctx.fillStyle=glow;ctx.fillRect(0,0,w,h);

      const artX=w*.075, artY=h*.105, artW=w*.85, artH=Math.min(h*.455,w*.85);
      if(ui.artwork) drawArtwork(ctx,art,artX,artY,artW,artH,accent,artworkRadius(opts.artworkShape,artW,artH,Math.max(16,w*.045)));
      const titleY=artY+artH+h*.060;
      if(ui.title){ctx.fillStyle='#ffffff';ctx.font=fontForStyle(style,true,Math.max(23,w*.070),typography);ctx.fillText(fitText(ctx,title,w*.84),w*.075,titleY);}
      if(ui.artist){ctx.fillStyle='#b9bfbb';ctx.font=fontForStyle(style,false,Math.max(13,w*.043),typography);ctx.fillText(fitText(ctx,artist,w*.84),w*.075,titleY+h*.044);}

      const barX=w*.075,barY=h*.705,barW=w*.85,barH=Math.max(6,h*.0065);
      if(ui.progress){roundRect(ctx,barX,barY,barW,barH,barH/2,'#3b413d');roundRect(ctx,barX,barY,barW*progress,barH,barH/2,'#1ED760');}
      ctx.fillStyle='#adb4af';ctx.font='500 '+Math.max(10,w*.032)+'px sans-serif';ctx.fillText(currentTime,barX,barY+h*.032);ctx.textAlign='right';ctx.fillText(durationTime,barX+barW,barY+h*.032);ctx.textAlign='left';

      const cy=h*.835;
      ctx.fillStyle='#c5cbc7';ctx.font='700 '+Math.max(21,w*.056)+'px sans-serif';ctx.textAlign='center';
      if(ui.shuffle)ctx.fillText('⇄',w*.15,cy+h*.012);ctx.fillText('◀',w*.32,cy+h*.010);ctx.fillText('▶',w*.68,cy+h*.010);if(ui.repeat)ctx.fillText('↻',w*.85,cy+h*.012);
      ctx.fillStyle='#1ED760';ctx.beginPath();ctx.arc(w*.50,cy,w*.098,0,Math.PI*2);ctx.fill();
      ctx.fillStyle='#071009';ctx.font='800 '+Math.max(24,w*.072)+'px sans-serif';ctx.fillText(playing?'Ⅱ':'▶',w*.50,cy+h*.014);
      ctx.textAlign='left';
      if(ui.waves!==false)drawEqualizer(ctx,w*.075,h*.915,w*.29,h*.052,'#1ED760',tick);
      if(ui.modeText){ctx.fillStyle='#7d847f';ctx.font='650 '+Math.max(10,w*.030)+'px sans-serif';ctx.fillText('EN REPRODUCCIÓN',w*.67,h*.946);}
      return;
    }


    // Non-immersive 2D skins: NO CARD / NO OUTER FRAME / NO DUPLICATED TITLE.
    // The native Android title, seekbar and controls remain below this stage.
    if(style==='neon'){
      const glow=ctx.createRadialGradient(w*.50,h*.44,w*.04,w*.50,h*.44,w*.60);
      glow.addColorStop(0,hexToRgba(accent,.22));glow.addColorStop(.62,hexToRgba(accent,.08));glow.addColorStop(1,'rgba(0,0,0,0)');ctx.fillStyle=glow;ctx.fillRect(0,0,w,h);
      for(let i=0;i<14;i++){const a=(i/14)*Math.PI*2+(tick*.0004);const rr=Math.min(w,h)*(.36+.025*Math.sin(tick*.001+i));ctx.fillStyle=hexToRgba(accent,i%3===0?.22:.10);ctx.beginPath();ctx.arc(w*.5+Math.cos(a)*rr,h*.43+Math.sin(a)*rr*.65,Math.max(2,w*.007),0,Math.PI*2);ctx.fill();}
    } else if(style==='glass'){
      const g1=ctx.createRadialGradient(w*.20,h*.18,0,w*.20,h*.18,w*.52);g1.addColorStop(0,'rgba(77,189,255,.20)');g1.addColorStop(1,'rgba(77,189,255,0)');ctx.fillStyle=g1;ctx.fillRect(0,0,w,h);
      const g2=ctx.createRadialGradient(w*.82,h*.72,0,w*.82,h*.72,w*.45);g2.addColorStop(0,hexToRgba(accent,.16));g2.addColorStop(1,'rgba(0,0,0,0)');ctx.fillStyle=g2;ctx.fillRect(0,0,w,h);
    } else if(style==='classic'){
      // Sin adornos extra debajo de la portada; la UI nativa ya muestra progreso/controles.
    }

    const maxArtW=w*(style==='minimal'?.82:.925),maxArtH=h*.82,artSize=Math.min(maxArtW,maxArtH);
    const artX=(w-artSize)/2,artY=h*.055;
    ctx.save();
    if(style==='neon'){ctx.shadowColor=accent;ctx.shadowBlur=Math.max(18,w*.055);}
    else if(style==='glass'){ctx.shadowColor='rgba(120,200,255,.32)';ctx.shadowBlur=Math.max(14,w*.040);}
    else {ctx.shadowColor='rgba(0,0,0,.30)';ctx.shadowBlur=Math.max(10,w*.030);}
    drawArtwork(ctx,art,artX,artY,artSize,artSize,accent,artworkRadius(opts.artworkShape,artSize,artSize,style==='minimal'?8:Math.max(12,w*.035)));
    ctx.restore();

    if(style==='glass'){
      // Sin puntos/decoraciones flotantes alrededor de la portada.
    }
  }

  function hexToRgba(hex,alpha){
    const value=(hex||'#8B5CF6').replace('#','');
    const n=parseInt(value.length===3?value.split('').map(x=>x+x).join(''):value,16);
    if(!Number.isFinite(n)) return 'rgba(139,92,246,'+alpha+')';
    return 'rgba('+((n>>16)&255)+','+((n>>8)&255)+','+(n&255)+','+alpha+')';
  }

  function get2DControlAt(style,nx,ny){
    if(style==='spotify2d'){
      if(ny>.695&&ny<.755&&nx>.07&&nx<.93) return {cmd:'seek',ratio:(nx-.075)/.85};
      if(ny>.76&&ny<.91){
        if(nx>.08&&nx<.23)return {cmd:'shuffle'};
        if(nx>.24&&nx<.40)return {cmd:'previous'};
        if(nx>.40&&nx<.60)return {cmd:'playpause'};
        if(nx>.60&&nx<.76)return {cmd:'next'};
        if(nx>.77&&nx<.92)return {cmd:'repeat'};
      }
    }
    return null;
  }

  function col(hex,a=1){return M.rgba(hex,a);}
  function addBox(model,w,h,d,color,pos=[0,0,0],rot=[0,0,0],scale=[1,1,1]){ return model.add(new M.Mesh(model.renderer,M.box(w,h,d),{color:col(color),pos,rot,scale})); }
  function addPlane(model,w,h,tex,pos=[0,0,0],rot=[0,0,0]){ return model.add(new M.Mesh(model.renderer,M.plane(w,h),{texture:tex,pos,rot,color:[1,1,1,1]})); }
  function addCylinder(model,r,d,color,pos=[0,0,0],rot=[0,0,0],segments=20){ return model.add(new M.Mesh(model.renderer,M.cylinder(r,d,segments),{color:col(color),pos,rot})); }

  function buildNES(renderer,tex){
    const m=new M.Model(renderer); m.ownTexture(tex);
    addBox(m,3.6,4.7,.62,'#4e5057',[0,0,0]); addBox(m,3.28,.30,.72,'#2d2f34',[0,2.06,.04]);
    for(let i=-4;i<=4;i++) addBox(m,.18,.13,.73,'#383a40',[i*.34,1.88,.05]); addBox(m,3.18,.26,.70,'#292b2f',[0,-2.03,.04]);
    addPlane(m,2.85,3.10,tex,[0,.18,.325]); addBox(m,.14,3.3,.70,'#656870',[-1.66,.10,.02]); addBox(m,.14,3.3,.70,'#656870',[1.66,.10,.02]);
    m.cameraZ=8.5; m.scale=.92; return m;
  }
  function buildCassette(renderer,tex){
    const m=new M.Model(renderer);m.ownTexture(tex);
    addBox(m,4.8,3.0,.62,'#ded5bf',[0,0,0]); addBox(m,4.25,1.18,.16,'#c7bfaa',[0,-.42,.39]); addPlane(m,4.25,1.65,tex,[0,.62,.32]);
    addCylinder(m,.52,.28,'#eae8e0',[-1.05,-.38,.48]); addCylinder(m,.52,.28,'#eae8e0',[1.05,-.38,.48]); addCylinder(m,.30,.32,'#34312d',[-1.05,-.38,.55]); addCylinder(m,.30,.32,'#34312d',[1.05,-.38,.55]);
    for(let i=0;i<6;i++){ const a=i*Math.PI/3; addCylinder(m,.075,.34,'#f7f6f1',[-1.05+Math.cos(a)*.20,-.38+Math.sin(a)*.20,.60],[],10); addCylinder(m,.075,.34,'#f7f6f1',[1.05+Math.cos(a)*.20,-.38+Math.sin(a)*.20,.60],[],10); }
    addBox(m,3.4,.12,.72,'#726d61',[0,-1.25,.02]); m.cameraZ=8.2;m.scale=.92;return m;
  }
  function buildGameboy3D(renderer,tex){
    const m=new M.Model(renderer);m.ownTexture(tex);
    addBox(m,3.55,5.4,.64,'#cbd3bd',[0,0,0]); addBox(m,3.10,2.55,.16,'#4f5950',[0,.95,.40]); addPlane(m,2.60,2.10,tex,[0,1.00,.492]);
    addBox(m,1.05,.30,.24,'#555862',[-.80,-1.30,.43]); addBox(m,.30,1.05,.24,'#555862',[-.80,-1.30,.43]); addCylinder(m,.30,.22,'#7a3b73',[.76,-1.20,.46]); addCylinder(m,.30,.22,'#7a3b73',[1.28,-.80,.46]);
    addBox(m,.68,.14,.20,'#777d70',[-.40,-2.05,.43],[0,0,-.22]); addBox(m,.68,.14,.20,'#777d70',[.45,-2.05,.43],[0,0,-.22]); addBox(m,2.8,.10,.20,'#70589e',[0,2.35,.43]);
    m.cameraZ=8.9;m.scale=.88;return m;
  }
  function buildWalkman(renderer,tex){
    const m=new M.Model(renderer);m.ownTexture(tex);
    addBox(m,4.0,4.9,.72,'#657080',[0,0,0]); addBox(m,3.55,.16,.82,'#3456aa',[0,2.10,.02]); addBox(m,3.45,2.75,.20,'#26303d',[0,.55,.46]); addPlane(m,3.05,2.35,tex,[0,.55,.575]);
    for(let i=0;i<4;i++)addBox(m,.55,.28,.82,'#2e333a',[-1.15+i*.77,2.58,.02]); addCylinder(m,.20,.22,'#dfe4ea',[1.50,-1.75,.48]); addCylinder(m,.16,.22,'#dfe4ea',[1.05,-1.75,.48]); addBox(m,2.1,.20,.20,'#d6dce3',[-.35,-1.75,.48]);
    m.cameraZ=8.8;m.scale=.90;return m;
  }
  function buildFloppy(renderer,tex){
    const m=new M.Model(renderer);m.ownTexture(tex);
    addBox(m,3.9,4.7,.55,'#2f3138',[0,0,0]); addBox(m,2.55,.80,.18,'#b8bdc6',[0,1.55,.37]); addPlane(m,2.95,2.65,tex,[0,-.20,.291]); addBox(m,.62,.28,.20,'#17181c',[0,-2.02,.38]);
    m.cameraZ=8.6;m.scale=.90;return m;
  }
  function buildVinyl(renderer,tex){
    const m=new M.Model(renderer);m.ownTexture(tex);
    addCylinder(m,2.35,.18,'#111216',[0,0,0]); addCylinder(m,.64,.22,'#b84f47',[0,0,.12]); addPlane(m,1.05,1.05,tex,[0,0,.245]);
    addBox(m,.10,3.0,.10,'#c4c8cf',[2.25,.55,.10],[0,0,-.34]); addCylinder(m,.24,.18,'#666b72',[2.55,1.82,.08]);
    m.cameraZ=8.0;m.scale=.94;m.spinDisc=true;return m;
  }

  function buildModel(renderer,style,texture){
    switch(style){
      case 'pixel': return buildNES(renderer,texture);
      case 'cassette': return buildCassette(renderer,texture);
      case 'gameboy3d': return buildGameboy3D(renderer,texture);
      case 'walkman': return buildWalkman(renderer,texture);
      case 'retro': return buildFloppy(renderer,texture);
      case 'vinyl': return buildVinyl(renderer,texture);
      default: return null;
    }
  }
  function is2DStyle(style){ return STYLES_2D.indexOf(style)>=0; }
  function is3DStyle(style){ return STYLES_3D.indexOf(style)>=0; }

  global.PlayerModels={
    makeLabelCanvas,buildModel,drawSkin2D,get2DControlAt,is2DStyle,is3DStyle,
    styles3d:STYLES_3D,styles2d:STYLES_2D,styles:STYLES_3D.concat(STYLES_2D)
  };
})(window);
