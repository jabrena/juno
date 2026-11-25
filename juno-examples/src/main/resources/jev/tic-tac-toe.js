const board=document.getElementById('board'),msg=document.getElementById('msg'),cells=[];
const lines=[[0,1,2],[3,4,5],[6,7,8],[0,3,6],[1,4,7],[2,5,8],[0,4,8],[2,4,6]];
const text={turn:'Your turn',thinking:'Model is thinking...',x:'The model wins',o:'You win!',draw:'Draw'};
let busy=false,state={board:'---------',status:'turn'};
for(let i=0;i<9;i++){const c=document.createElement('button');c.className='cell';c.onclick=()=>play(i);board.append(c);cells.push(c)}
function draw(){const done=state.status!=='turn';const win=lines.find(l=>l.every(i=>state.board[i]!=='-'&&state.board[i]===state.board[l[0]]))||[];cells.forEach((c,i)=>{const v=state.board[i];c.textContent=v==='-'?'':v;c.className='cell '+(v==='-'?'':v)+(win.includes(i)?' win':'');c.disabled=busy||done||v!=='-'});msg.textContent=busy?text.thinking:text[state.status]}
const wait=ms=>new Promise(r=>setTimeout(r,ms));
function request(path,body){return fetch(path,body===undefined?{cache:'no-store'}:{method:'POST',body:body}).then(r=>{if(!r.ok)throw new Error('HTTP '+r.status);return r.json()})}
async function call(path,body,applied){busy=true;draw();let error;for(let attempt=0;attempt<4;attempt++){try{if(attempt>0){await wait(700*attempt);if(applied){const s=await request('/api/state');if(applied(s)){state=s;busy=false;draw();return}}}state=await request(path,body);busy=false;draw();return}catch(e){error=e}}busy=false;draw();msg.textContent='Board busy ('+error.message+'), try again'}
function play(i){if(busy||state.board[i]!=='-')return;state.board=state.board.slice(0,i)+'O'+state.board.slice(i+1);call('/api/move',''+i,s=>s.board[i]==='O')}
document.getElementById('reset').onclick=()=>call('/api/reset','',s=>s.board.indexOf('O')<0&&s.status==='turn');
call('/api/state');
