(function(){
  const qs=new URLSearchParams(location.search), selected=qs.get('selected')||'pixel';
  const selectedAccent='#'+(qs.get('accent')||'8B5CF6');
  const xpOptions={xpTheme:qs.get('xpTheme')||'blue',xpDesktopColor:'#'+(qs.get('xpDesktop')||'386DA4'),xpTitle:qs.get('xpTitle')||'Reproductor de medios',xpPixelated:(qs.get('xpAspect')||'pixel')!=='sharp',xpDisplay:qs.get('xpDisplay')||'cover'};
  const phoneOptions={phoneTheme:qs.get('phoneTheme')||'sky',phoneTitle:qs.get('phoneTitle')||'Reproductor',phonePixelated:(qs.get('phoneAspect')||'pixel')!=='sharp',phoneLcd:qs.get('phoneLcd')!=='false',phoneCard:qs.get('phoneCard')||'cover'};
  const sections=[
    {title:'OBJETOS 3D', items:[['pixel','Cartucho NES 3D'],['cassette','Cassette 3D'],['gameboy3d','Game Boy 3D'],['walkman','Walkman 3D'],['retro','Floppy Disk 3D'],['vinyl','Vinilo 3D']]},
    {title:'SKINS 2D INMERSIVAS', items:[['spotify2d','Spotify 2D'],['xp2d','Reproductor XP'],['phone2d','Teléfono clásico']]},
    {title:'VISUALIZADORES', items:[['equalizer2d','Ecualizador 2D'],['ring','Anillo']]},
    {title:'SKINS 2D CLÁSICAS', items:[['classic','Clásico 2D'],['neon','Neón 2D'],['glass','Glass 2D'],['minimal','Minimal 2D']]}
  ];
  const defaultAccent={pixel:'#FF4D5A',cassette:'#FF8A3D',gameboy3d:'#4F7DFF',walkman:'#4F7DFF',retro:'#24C7FF',vinyl:'#FF5FA2',spotify2d:'#1ED760',xp2d:'#3d87d6',phone2d:'#60b7dd',classic:'#8B5CF6',neon:'#8B5CF6',glass:'#24C7FF',minimal:'#00BFA5',equalizer2d:'#8B5CF6',ring:'#39FF7D'};
  const defaultTypography={pixel:'mono',cassette:'condensed',gameboy3d:'mono',walkman:'condensed',retro:'mono',vinyl:'serif',spotify2d:'modern',xp2d:'modern',phone2d:'condensed',classic:'modern',neon:'condensed',glass:'rounded',minimal:'modern',equalizer2d:'modern',ring:'modern'};
  const grid=document.getElementById('grid'); const hidden=document.getElementById('hidden');
  const renderer=new Mini3D.Renderer(hidden,{preserveDrawingBuffer:true});
  const labelByStyle={};

  function renderSection(section){
    const title=document.createElement('div'); title.className='section'; title.textContent=section.title; grid.appendChild(title);
    const wrap=document.createElement('div'); wrap.className='grid2'; grid.appendChild(wrap);
    section.items.forEach(([style,name])=>{
      const card=document.createElement('button');card.className='card'+(style===selected?' selected':'');card.type='button';
      if(style===selected){card.style.borderColor=selectedAccent;card.style.boxShadow='0 0 0 1px '+selectedAccent+'55';}
      const cv=document.createElement('canvas');cv.width=220;cv.height=260;cv.className='preview';
      const title=document.createElement('div');title.className='name';title.textContent=name;
      card.append(cv,title);wrap.appendChild(card); labelByStyle[style]=cv;
      card.onclick=()=>{location.href='app://select?skin='+encodeURIComponent(style);};
    });
  }
  sections.forEach(renderSection);

  function renderStyle(style,canvas){
    const accent=defaultAccent[style]||'#8B5CF6';
    const typography=defaultTypography[style]||'modern';
    const ctx=canvas.getContext('2d'); ctx.imageSmoothingEnabled=false; ctx.clearRect(0,0,canvas.width,canvas.height);
    if(PlayerModels.is2DStyle(style)){
      if(style==='phone2d'){PlayerModels.drawSkin2D(canvas,style,{title:'Tu canción',artist:'Tu artista',album:'Tu álbum',playing:true,hasMedia:true,previewMode:true,progress:.46,positionMs:73000,durationMs:215000,tick:0,animation:0,favorite:true,favoriteVisible:true,queueVisible:true,queuePosition:13,queueTotal:13,radio:false,...phoneOptions,ui:{artwork:true,title:true,artist:true,progress:true,shuffle:true,repeat:true,modeText:true}});return;}
      if(style==='xp2d'){PlayerModels.drawSkin2D(canvas,style,{title:'',artist:'',playing:true,hasMedia:true,previewMode:true,progress:.46,positionMs:73000,durationMs:215000,tick:900,animation:0,favorite:false,radio:false,ui:{artwork:true},...xpOptions});return;}
      PlayerModels.drawSkin2D(canvas,style,{accent,title:'',artist:'',artImage:null,playing:true,animation:1,previewMode:true,progress:.46,tick:900,typography,immersive:style==='spotify2d',ui:{artwork:true,title:false,artist:false,progress:true,waves:true,shuffle:true,repeat:true,modeText:false}});
      return;
    }
    hidden.style.width='260px';hidden.style.height='300px';
    const label=PlayerModels.makeLabelCanvas(style,'','',null,accent,typography,true);
    const tex=renderer.createTexture(label);const model=PlayerModels.buildModel(renderer,style,tex);renderer.cameraZ=model.cameraZ||8.4;
    renderer.render(model,{pos:[0,0,0],rot:[-.08,.62,0],scale:[model.scale||1,model.scale||1,model.scale||1]});
    ctx.drawImage(hidden,0,0,canvas.width,canvas.height);model.dispose();
  }
  requestAnimationFrame(()=>sections.forEach(sec=>sec.items.forEach(([s])=>renderStyle(s,labelByStyle[s]))));
  window.addEventListener('phone-font-ready',()=>renderStyle('phone2d',labelByStyle.phone2d));
})();
