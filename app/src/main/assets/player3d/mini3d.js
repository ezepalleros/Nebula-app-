(function(global){
  'use strict';

  function mat4Identity(){ return new Float32Array([1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1]); }
  function mat4Multiply(a,b){
    const o=new Float32Array(16);
    for(let r=0;r<4;r++) for(let c=0;c<4;c++) {
      o[c*4+r]=a[0*4+r]*b[c*4+0]+a[1*4+r]*b[c*4+1]+a[2*4+r]*b[c*4+2]+a[3*4+r]*b[c*4+3];
    }
    return o;
  }
  function mat4Perspective(fov,aspect,near,far){
    const f=1/Math.tan(fov/2), nf=1/(near-far);
    return new Float32Array([f/aspect,0,0,0, 0,f,0,0, 0,0,(far+near)*nf,-1, 0,0,(2*far*near)*nf,0]);
  }
  function mat4Translate(x,y,z){ const m=mat4Identity(); m[12]=x;m[13]=y;m[14]=z;return m; }
  function mat4Scale(x,y,z){ const m=mat4Identity(); m[0]=x;m[5]=y;m[10]=z;return m; }
  function mat4RotateX(a){ const c=Math.cos(a),s=Math.sin(a); return new Float32Array([1,0,0,0, 0,c,s,0, 0,-s,c,0, 0,0,0,1]); }
  function mat4RotateY(a){ const c=Math.cos(a),s=Math.sin(a); return new Float32Array([c,0,-s,0, 0,1,0,0, s,0,c,0, 0,0,0,1]); }
  function mat4RotateZ(a){ const c=Math.cos(a),s=Math.sin(a); return new Float32Array([c,s,0,0, -s,c,0,0, 0,0,1,0, 0,0,0,1]); }
  function compose(p,r,s){
    let m=mat4Translate(p[0],p[1],p[2]);
    m=mat4Multiply(m,mat4RotateY(r[1]));
    m=mat4Multiply(m,mat4RotateX(r[0]));
    m=mat4Multiply(m,mat4RotateZ(r[2]));
    m=mat4Multiply(m,mat4Scale(s[0],s[1],s[2]));
    return m;
  }

  function createShader(gl,type,src){ const s=gl.createShader(type); gl.shaderSource(s,src); gl.compileShader(s); if(!gl.getShaderParameter(s,gl.COMPILE_STATUS)) throw new Error(gl.getShaderInfoLog(s)); return s; }
  function createProgram(gl,vs,fs){ const p=gl.createProgram(); gl.attachShader(p,createShader(gl,gl.VERTEX_SHADER,vs)); gl.attachShader(p,createShader(gl,gl.FRAGMENT_SHADER,fs)); gl.linkProgram(p); if(!gl.getProgramParameter(p,gl.LINK_STATUS)) throw new Error(gl.getProgramInfoLog(p)); return p; }

  const VS=`
    attribute vec3 aPosition;
    attribute vec3 aNormal;
    attribute vec2 aUV;
    uniform mat4 uMVP;
    uniform mat4 uModel;
    varying vec3 vNormal;
    varying vec2 vUV;
    void main(){
      gl_Position=uMVP*vec4(aPosition,1.0);
      vNormal=normalize(mat3(uModel[0].xyz,uModel[1].xyz,uModel[2].xyz)*aNormal);
      vUV=aUV;
    }`;
  const FS=`
    precision mediump float;
    varying vec3 vNormal;
    varying vec2 vUV;
    uniform vec4 uColor;
    uniform bool uUseTex;
    uniform sampler2D uTexture;
    uniform vec3 uLightDir;
    uniform float uAmbient;
    void main(){
      vec4 base=uUseTex?texture2D(uTexture,vUV):uColor;
      float d=max(dot(normalize(vNormal),normalize(uLightDir)),0.0);
      float light=uAmbient+(1.0-uAmbient)*d;
      gl_FragColor=vec4(base.rgb*light,base.a);
    }`;

  class Mesh {
    constructor(renderer, geo, opts={}){
      const gl=renderer.gl; this.renderer=renderer;
      this.pos=opts.pos||[0,0,0]; this.rot=opts.rot||[0,0,0]; this.scale=opts.scale||[1,1,1];
      this.color=opts.color||[1,1,1,1]; this.texture=opts.texture||null;
      this.count=geo.indices.length;
      this.vbo=gl.createBuffer(); gl.bindBuffer(gl.ARRAY_BUFFER,this.vbo); gl.bufferData(gl.ARRAY_BUFFER,new Float32Array(geo.vertices),gl.STATIC_DRAW);
      this.nbo=gl.createBuffer(); gl.bindBuffer(gl.ARRAY_BUFFER,this.nbo); gl.bufferData(gl.ARRAY_BUFFER,new Float32Array(geo.normals),gl.STATIC_DRAW);
      this.tbo=gl.createBuffer(); gl.bindBuffer(gl.ARRAY_BUFFER,this.tbo); gl.bufferData(gl.ARRAY_BUFFER,new Float32Array(geo.uvs),gl.STATIC_DRAW);
      this.ibo=gl.createBuffer(); gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER,this.ibo); gl.bufferData(gl.ELEMENT_ARRAY_BUFFER,new Uint16Array(geo.indices),gl.STATIC_DRAW);
    }
    dispose(){ const gl=this.renderer.gl; [this.vbo,this.nbo,this.tbo,this.ibo].forEach(b=>b&&gl.deleteBuffer(b)); }
  }

  class Model {
    constructor(renderer){ this.renderer=renderer; this.meshes=[]; this.textures=[]; }
    add(mesh){ this.meshes.push(mesh); return mesh; }
    ownTexture(tex){ if(tex) this.textures.push(tex); return tex; }
    dispose(){ this.meshes.forEach(m=>m.dispose()); this.meshes=[]; const gl=this.renderer.gl; this.textures.forEach(t=>gl.deleteTexture(t)); this.textures=[]; }
  }

  class Renderer {
    constructor(canvas,opts={}){
      this.canvas=canvas;
      const gl=canvas.getContext('webgl',{alpha:true,antialias:false,preserveDrawingBuffer:!!opts.preserveDrawingBuffer,premultipliedAlpha:true});
      if(!gl) throw new Error('WebGL unavailable');
      this.gl=gl; this.program=createProgram(gl,VS,FS);
      this.aPos=gl.getAttribLocation(this.program,'aPosition'); this.aNorm=gl.getAttribLocation(this.program,'aNormal'); this.aUV=gl.getAttribLocation(this.program,'aUV');
      this.uMVP=gl.getUniformLocation(this.program,'uMVP'); this.uModel=gl.getUniformLocation(this.program,'uModel'); this.uColor=gl.getUniformLocation(this.program,'uColor'); this.uUseTex=gl.getUniformLocation(this.program,'uUseTex'); this.uTexture=gl.getUniformLocation(this.program,'uTexture'); this.uLightDir=gl.getUniformLocation(this.program,'uLightDir'); this.uAmbient=gl.getUniformLocation(this.program,'uAmbient');
      gl.enable(gl.DEPTH_TEST); gl.depthFunc(gl.LEQUAL); gl.enable(gl.CULL_FACE); gl.cullFace(gl.BACK); gl.enable(gl.BLEND); gl.blendFunc(gl.SRC_ALPHA,gl.ONE_MINUS_SRC_ALPHA);
      this.cameraZ=8.2;
    }
    resize(){
      const dpr=Math.min(window.devicePixelRatio||1,2), w=Math.max(1,Math.floor(this.canvas.clientWidth*dpr)), h=Math.max(1,Math.floor(this.canvas.clientHeight*dpr));
      if(this.canvas.width!==w||this.canvas.height!==h){this.canvas.width=w;this.canvas.height=h;}
      this.gl.viewport(0,0,w,h);
    }
    createTexture(source){
      const gl=this.gl, tex=gl.createTexture(); gl.bindTexture(gl.TEXTURE_2D,tex); gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL,true);
      gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.NEAREST); gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.NEAREST); gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE); gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE);
      gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,gl.RGBA,gl.UNSIGNED_BYTE,source); return tex;
    }
    updateTexture(tex,source){ const gl=this.gl; gl.bindTexture(gl.TEXTURE_2D,tex); gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL,true); gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,gl.RGBA,gl.UNSIGNED_BYTE,source); }
    render(model,root){
      this.resize(); const gl=this.gl; gl.clearColor(0,0,0,0); gl.clear(gl.COLOR_BUFFER_BIT|gl.DEPTH_BUFFER_BIT); if(!model)return;
      gl.useProgram(this.program); gl.uniform3f(this.uLightDir,-0.42,0.75,0.58); gl.uniform1f(this.uAmbient,0.34);
      const aspect=this.canvas.width/this.canvas.height; const proj=mat4Perspective(Math.PI/4.2,aspect,.1,100); const view=mat4Translate(0,0,-this.cameraZ);
      let rootM=compose(root.pos||[0,0,0],root.rot||[0,0,0],root.scale||[1,1,1]);
      model.meshes.forEach(mesh=>{
        const local=compose(mesh.pos,mesh.rot,mesh.scale); const modelM=mat4Multiply(rootM,local); const mvp=mat4Multiply(mat4Multiply(proj,view),modelM);
        gl.uniformMatrix4fv(this.uMVP,false,mvp); gl.uniformMatrix4fv(this.uModel,false,modelM); gl.uniform4fv(this.uColor,new Float32Array(mesh.color));
        gl.bindBuffer(gl.ARRAY_BUFFER,mesh.vbo); gl.enableVertexAttribArray(this.aPos); gl.vertexAttribPointer(this.aPos,3,gl.FLOAT,false,0,0);
        gl.bindBuffer(gl.ARRAY_BUFFER,mesh.nbo); gl.enableVertexAttribArray(this.aNorm); gl.vertexAttribPointer(this.aNorm,3,gl.FLOAT,false,0,0);
        gl.bindBuffer(gl.ARRAY_BUFFER,mesh.tbo); gl.enableVertexAttribArray(this.aUV); gl.vertexAttribPointer(this.aUV,2,gl.FLOAT,false,0,0);
        if(mesh.texture){ gl.activeTexture(gl.TEXTURE0); gl.bindTexture(gl.TEXTURE_2D,mesh.texture); gl.uniform1i(this.uTexture,0); gl.uniform1i(this.uUseTex,1); } else gl.uniform1i(this.uUseTex,0);
        gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER,mesh.ibo); gl.drawElements(gl.TRIANGLES,mesh.count,gl.UNSIGNED_SHORT,0);
      });
    }
    dispose(){ const gl=this.gl; if(this.program) gl.deleteProgram(this.program); }
  }

  function box(w,h,d){
    const x=w/2,y=h/2,z=d/2;
    const faces=[
      [[-x,-y,z],[x,-y,z],[x,y,z],[-x,y,z],[0,0,1]],
      [[x,-y,-z],[-x,-y,-z],[-x,y,-z],[x,y,-z],[0,0,-1]],
      [[-x,-y,-z],[-x,-y,z],[-x,y,z],[-x,y,-z],[-1,0,0]],
      [[x,-y,z],[x,-y,-z],[x,y,-z],[x,y,z],[1,0,0]],
      [[-x,y,z],[x,y,z],[x,y,-z],[-x,y,-z],[0,1,0]],
      [[-x,-y,-z],[x,-y,-z],[x,-y,z],[-x,-y,z],[0,-1,0]]
    ];
    const v=[],n=[],uv=[],idx=[]; let k=0;
    faces.forEach(f=>{ for(let i=0;i<4;i++){v.push(...f[i]); n.push(...f[4]);} uv.push(0,0,1,0,1,1,0,1); idx.push(k,k+1,k+2,k,k+2,k+3); k+=4; });
    return {vertices:v,normals:n,uvs:uv,indices:idx};
  }
  function plane(w,h){ return {vertices:[-w/2,-h/2,0,w/2,-h/2,0,w/2,h/2,0,-w/2,h/2,0],normals:[0,0,1,0,0,1,0,0,1,0,0,1],uvs:[0,0,1,0,1,1,0,1],indices:[0,1,2,0,2,3]}; }
  function cylinder(r,d,segments=20){
    const v=[],n=[],uv=[],idx=[]; let k=0;
    for(let i=0;i<segments;i++){
      const a0=i/segments*Math.PI*2,a1=(i+1)/segments*Math.PI*2; const x0=Math.cos(a0)*r,y0=Math.sin(a0)*r,x1=Math.cos(a1)*r,y1=Math.sin(a1)*r;
      v.push(x0,y0,d/2,x1,y1,d/2,x1,y1,-d/2,x0,y0,-d/2); n.push(Math.cos(a0),Math.sin(a0),0,Math.cos(a1),Math.sin(a1),0,Math.cos(a1),Math.sin(a1),0,Math.cos(a0),Math.sin(a0),0); uv.push(0,0,1,0,1,1,0,1); idx.push(k,k+1,k+2,k,k+2,k+3);k+=4;
    }
    const front=k; v.push(0,0,d/2);n.push(0,0,1);uv.push(.5,.5);k++;
    for(let i=0;i<=segments;i++){const a=i/segments*Math.PI*2;v.push(Math.cos(a)*r,Math.sin(a)*r,d/2);n.push(0,0,1);uv.push((Math.cos(a)+1)/2,(Math.sin(a)+1)/2);k++;}
    for(let i=0;i<segments;i++)idx.push(front,front+1+i,front+2+i);
    const back=k; v.push(0,0,-d/2);n.push(0,0,-1);uv.push(.5,.5);k++;
    for(let i=0;i<=segments;i++){const a=i/segments*Math.PI*2;v.push(Math.cos(a)*r,Math.sin(a)*r,-d/2);n.push(0,0,-1);uv.push((Math.cos(a)+1)/2,(Math.sin(a)+1)/2);k++;}
    for(let i=0;i<segments;i++)idx.push(back,back+2+i,back+1+i);
    return {vertices:v,normals:n,uvs:uv,indices:idx};
  }

  function rgba(hex,a=1){ const n=typeof hex==='number'?hex:parseInt(hex.replace('#',''),16); return [((n>>16)&255)/255,((n>>8)&255)/255,(n&255)/255,a]; }

  global.Mini3D={Renderer,Model,Mesh,box,plane,cylinder,rgba,math:{mat4Identity,mat4Multiply,compose}};
})(window);
